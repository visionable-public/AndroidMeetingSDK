package com.visionable.meetingrefapp.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.visionable.meetingrefapp.R
import com.visionable.meetingrefapp.SdkListener
import com.visionable.meetingrefapp.data.NetworkCameraItem
import com.visionable.meetingrefapp.data.VideoStreamItem
import com.visionable.meetingrefapp.databinding.MeetingFragmentBinding
import com.visionable.meetingrefapp.recyclerview.adapters.NetworkCameraRecyclerViewAdapter
import com.visionable.meetingrefapp.recyclerview.adapters.ParticipantRecyclerViewAdapter
import com.visionable.meetingrefapp.recyclerview.adapters.VideoRecyclerViewAdapter
import com.visionable.meetingsdk.MeetingSDK
import com.visionable.meetingsdk.Participant
import com.visionable.meetingsdk.VideoView
import com.visionable.meetingsdk.interfaces.INotificationCallback
import com.visionable.meetingsdk.interfaces.ISwitchCameraCallback

/**
 * Meeting Fragment that holds all of the video stream cards, meeting tools bottom sheet, and
 * participant bottom sheet
 *
 * Second screen in the flow
 */
class MeetingFragment : Fragment(), INotificationCallback {

    companion object {
        private val TAG = MeetingFragment::class.java.canonicalName

        private const val SPAN_COUNT = 2
        private const val STD_VOLUME = 50

        // Devices to be excluded from the spinner adapter lists
        private val EXCLUDED_DEVICES = listOf(
            MeetingSDK.ANDROID_SCREEN_CAPTURE
        )

        // Enum class to facilitate sending specific payloads to RecyclerViewAdapters
        enum class INotificationCases {
            PARTICIPANT_MUTED,
            PARTICIPANT_UNMUTED
        }
    }

    private var _binding: MeetingFragmentBinding? = null
    private val binding get() = _binding!!

    private var sdkListener: SdkListener? = null

    // Participant List
    private val participantList = mutableListOf<Participant>()
    private val videoStreamList = mutableListOf<VideoStreamItem>()
    private val networkCameraList = mutableListOf<NetworkCameraItem>()

    // Device Lists
    private var audioInputDevices: ArrayList<String> = ArrayList()
    private var audioOutputDevices: ArrayList<String> = ArrayList()
    private var videoDevices: ArrayList<String> = ArrayList()
    private var videoCodecs: Array<String> = arrayOf()

    private var isAudioEnabled = false
    private var isVideoEnabled = false

    // Bottom Sheet Behaviors
    private lateinit var meetingToolsBottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var participantBottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var networkCameraBottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    /**
     * Permissions callbacks
     * Will be fired if the app asks the user for a specific permission
     */
    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(TAG, "Audio permission has been granted by user")
                launchLocalAudio()
            } else {
                Log.i(TAG, "Audio permission has been denied by user")
                sdkListener?.showErrorModal(
                    title = R.string.enable_audio_alert_title,
                    message = R.string.audio_permission_alert_message
                )

                // Re-enable 'Enable Audio' checkbox & Audio Spinners so the user can try again
                binding.chooseDevicesLayout.apply {
                    audioInputSpinner.isEnabled = true
                    audioOutputSpinner.isEnabled = true
                }
            }
        }

    private val recordVideoPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(TAG, "Camera permission has been granted by user")
                launchLocalVideo()
            } else {
                Log.i(TAG, "Camera permission has been denied by user")
                sdkListener?.showErrorModal(
                    title = R.string.enable_video_alert_title,
                    message = R.string.video_permission_alert_message
                )

                // Re-enable 'Enable Video' checkbox & Video Spinners so the user can try again
                binding.chooseDevicesLayout.apply {
                    videoInputSpinner.isEnabled = true
                    videoCodecSpinner.isEnabled = true
                }
            }
        }

    // Enable participant menu icon
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        // Set as delegate
        MeetingSDK.setDelegate(this,Looper.getMainLooper())
    }

    /**
     * Remove all local participant streams when configuration changes, as they get re-added by the SDK
     *
     * @param newConfig
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Keep index separate from iterator as items get removed
        var currIndex = 0

        // Use iterator to prevent ConcurrentModificationException
        with(videoStreamList.iterator()) {
            while (this.hasNext()) {
                if (this.next().participant.isLocal) {
                    this.remove()
                    binding.videoGridLayout.adapter?.notifyItemRemoved(currIndex)
                } else {
                    currIndex++
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.participant_menu, menu)
    }

    /**
     * Click listener for menu items
     *
     * @param item
     * @return
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.view_attendees -> {
                // Toggle participant bottom sheet
                if (participantBottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    meetingToolsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    participantBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                } else {
                    participantBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MeetingFragmentBinding.inflate(inflater, container, false)

        setupBindingListeners()
        setupRecyclerAdapters()
        setupBottomSheetIcons()
        setupBottomSheetCallSettings()
        setupParticipantBottomSheet()
        setupNetworkCameraBottomSheet()

        getDevices()
        MeetingSDK.showActiveSpeaker(true)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        sdkListener = try {
            (context as SdkListener)
        } catch (castException: ClassCastException) {
            /** The activity does not implement the listener */
            Log.e(TAG, "Attached context does not implement SdkListener")
            null
        }
    }

    /**
     * Gets the video devices and video codecs and initializes the spinners containing their choices
     */
    private fun getDevices() {
        MeetingSDK.getAudioInputDevices(audioInputDevices)
        MeetingSDK.getAudioOutputDevices(audioOutputDevices)
        val screenDevices = ArrayList<String>()
        MeetingSDK.getVideoDevices(videoDevices, screenDevices)
        videoDevices.addAll(screenDevices)

        if (videoDevices.isNotEmpty()) {
            videoCodecs = MeetingSDK.getSupportedVideoSendResolutions(videoDevices[0])
        }

        binding.chooseDevicesLayout.apply {
            createSpinnerAdapter(audioInputDevices.toTypedArray())?.let {
                audioInputSpinner.adapter = it
                (binding.meetingToolsLayout.audioInputSpinner as? AutoCompleteTextView)?.setAdapter(it)
            }

            createSpinnerAdapter(audioOutputDevices.toTypedArray())?.let {
                audioOutputSpinner.adapter = it
                (binding.meetingToolsLayout.audioOutputSpinner as? AutoCompleteTextView)?.setAdapter(it)
            }

            createSpinnerAdapter(videoDevices.toTypedArray())?.let {
                videoInputSpinner.adapter = it
                (binding.meetingToolsLayout.videoDevicesSpinner as? AutoCompleteTextView)?.setAdapter(it)
            }

            createSpinnerAdapter(videoCodecs)?.let {
                videoCodecSpinner.adapter = it
                (binding.meetingToolsLayout.videoCodecSpinner as? AutoCompleteTextView)?.setAdapter(it)
            }
        }
    }

    /**
     * Helper Function
     * Toggles the local audio input (microphone)
     *
     * @param device -> [String]: if enabling audio, you must specify the device
     * @param isEnabled
     * @return
     */
    private fun toggleLocalAudioInput(device: String = "", isEnabled: Boolean): Boolean {
        return if (isEnabled) {
            MeetingSDK.enableAudioInput(device)
        } else {
            MeetingSDK.disableAudioInput()
        }
    }

    /**
     * Helper Function
     * Toggles the local audio output (speaker)
     *
     * @param device -> [String]: if enabling audio, you must specify the device
     * @param isEnabled
     * @return
     */
    private fun toggleLocalAudioOutput(device: String = "", isEnabled: Boolean): Boolean {
        return if (isEnabled) {
            MeetingSDK.enableAudioOutput(device)
        } else {
            MeetingSDK.disableAudioOutput()
        }
    }

    /**
     * Helper Function
     * Toggles the local video input (camera)
     *
     * @param device -> [String]: if enabling video, you must specify the device
     * @param codec -> [String]: if enabling video, you must specify the codec
     * @param isEnabled
     * @return
     */
    private fun toggleLocalVideo(device: String = "", codec: String = "", isEnabled: Boolean): Boolean {
        return if (isEnabled) {
            MeetingSDK.enableVideoCapture(device, codec)
        } else {
            MeetingSDK.disableVideoCapture()
        }
    }

    /**
     * Helper Function
     * Switches the local camera input currently in use using the same video codec previously being used
     * If successful, switch camera and set the corresponding values for the selector displays
     * If failure, show popup modal
     *
     * @param newCamera
     */
    private fun switchCamera(newCamera: String) {
        MeetingSDK.switchCamera(newCamera, object : ISwitchCameraCallback {
            override fun onCameraSwitched(p0: String?) {
                // Load codec options for new camera
                videoCodecs = MeetingSDK.getSupportedVideoSendResolutions(newCamera)

                // New Camera will utilize same codec as last one
                val lastUsedCodec = MeetingSDK.getVideoResolution()

                createSpinnerAdapter(videoCodecs)?.let { adapter ->
                    (binding.meetingToolsLayout.videoCodecSpinner as? AutoCompleteTextView)?.apply {
                        setAdapter(adapter)

                        // Set text using last used codec or, if not found, first available
                        setText(
                            videoCodecs.firstOrNull { it == lastUsedCodec } ?: videoCodecs[0],
                            false
                        )
                    }
                }
            }

            override fun onCameraSwitchFailed() {
                sdkListener?.showErrorModal(
                    title = R.string.change_camera_alert_title
                )
            }
        })
    }

    /**
     * Helper Function
     * Switches the local video codec currently in use
     * If successful, switches video codec
     * If failure, show popup modal
     *
     * @param resolution -> [String]: new video codec currently used, chosen out of the supported codecs
     * for that camera
     */
    private fun changeVideoResolution(resolution: String) {
        if (!MeetingSDK.changeVideoResolution(resolution)) {
            sdkListener?.showErrorModal(
                title = R.string.resolution_alert_title,
                message = R.string.resolution_alert_message
            )
        }
    }

    /**
     * Helper Function
     * Toggles the ability for the user to share their phone screen
     *
     * @param isEnabled -> [Boolean]: toggle to either enable or disable local screen sharing
     */
    private fun toggleScreenCapture(isEnabled: Boolean) {
        if (isEnabled) {
            MeetingSDK.enableScreenCapture(
                MeetingSDK.ANDROID_SCREEN_CAPTURE,
                "VP8 Best Screen Capture"
            )
        } else {
            MeetingSDK.disableScreenCapture()
        }
    }

    /**
     * Helper Function
     * Toggles local audio ON and makes some visual changes to progress flow
     */
    private fun launchLocalAudio() {
        with(binding) {
            val isInputEnabled = toggleLocalAudioInput(chooseDevicesLayout.audioInputSpinner.selectedItem.toString(), true)
            val isOutputEnabled = toggleLocalAudioOutput(chooseDevicesLayout.audioOutputSpinner.selectedItem.toString(), true)

            if (isInputEnabled && isOutputEnabled) {
                isAudioEnabled = true

                meetingToolsLayout.apply {
                    // Enables selected icons
                    toggleMicIcon.isChecked = true
                    toggleAudioIcon.isChecked = true

                    // Set Values chosen for the Bottom Sheet
                    audioInputSpinner.setText(chooseDevicesLayout.audioInputSpinner.selectedItem.toString(), false)
                    audioOutputSpinner.setText(chooseDevicesLayout.audioOutputSpinner.selectedItem.toString(), false)
                }
            } else {
                Log.i(TAG, "Local audio could not be enabled")
                sdkListener?.showErrorModal(
                    title = R.string.enable_audio_alert_title,
                    message = R.string.launch_local_audio_message
                )

                // Re-enable spinners for audio
                chooseDevicesLayout.apply {
                    audioInputSpinner.isEnabled = true
                    audioOutputSpinner.isEnabled = true
                }
            }
        }
    }

    /**
     * Helper Function
     * Toggles local video ON and makes some visual changes to progress flow
     */
    private fun launchLocalVideo() {
        with(binding) {
            val isCameraEnabled = MeetingSDK.enableVideoCapture(
                chooseDevicesLayout.videoInputSpinner.selectedItem.toString(),
                chooseDevicesLayout.videoCodecSpinner.selectedItem.toString()
            )

            if (isCameraEnabled) {
                isVideoEnabled = true

                meetingToolsLayout.apply {
                    // Enables selected icon
                    toggleVideoIcon.isChecked = true

                    // Set Values chosen for the Bottom Sheet
                    videoDevicesSpinner.setText(chooseDevicesLayout.videoInputSpinner.selectedItem.toString(), false)
                    videoCodecSpinner.setText(chooseDevicesLayout.videoCodecSpinner.selectedItem.toString(), false)
                }
            } else {
                Log.i(TAG, "Local video could not be enabled")
                sdkListener?.showErrorModal(
                    title = R.string.enable_video_alert_title,
                    message = R.string.launch_local_video_message
                )

                // Re-enable Video Spinners so the user can try again
                chooseDevicesLayout.apply {
                    videoInputSpinner.isEnabled = true
                    videoCodecSpinner.isEnabled = true
                }
            }
        }
    }

    /**
     * Makes call to the SDK to exit meeting
     * Navigate back to [SetUpFragment]
     */
    private fun exitMeeting() {
        // Call to the MeetingSDK
        MeetingSDK.exitMeeting()

        // Navigate to SetUpFragment
        parentFragmentManager.commit {
            setReorderingAllowed(true)
            replace<SetUpFragment>(R.id.container)
        }
    }

    /**
     * Helper function to create either an [ArrayAdapter] from the options provided or the standard
     * "Default Device", and apply certain settings
     *
     * @param options - Default Value: Empty Array - array of options to populate ArrayAdapter
     * @return - instantiated [ArrayAdapter] or null iff context is null
     */
    private fun createSpinnerAdapter(options: Array<String> = arrayOf()): ArrayAdapter<String>? {
        val filteredOptions = options.filterNot { it in EXCLUDED_DEVICES }

        return context?.let {
            return if (filteredOptions.isNotEmpty()) {
                ArrayAdapter(it, android.R.layout.simple_spinner_item, filteredOptions)
            } else {
                val stringArray = resources.getStringArray(R.array.devices_array)
                ArrayAdapter(it, android.R.layout.simple_spinner_item, stringArray)
            }.also { adapter ->
                // Specify the layout to use when the list of choices appears
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
    }

    /**
     * Helper function
     * Sets up the relevant UI values and sets click listeners for items in the main layout view binding
     */
    private fun setupBindingListeners() {
        // Set up Bottom Sheet Behaviors
        meetingToolsBottomSheetBehavior = BottomSheetBehavior.from(binding.meetingToolsLayout.root)
        participantBottomSheetBehavior = BottomSheetBehavior.from(binding.participantsLayout.root)
        networkCameraBottomSheetBehavior = BottomSheetBehavior.from(binding.networkCameraManagerLayout.root)

        with(binding) {
            chooseDevicesLayout.apply {
                videoInputSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    // Boolean is important to ensure `onItemSelected` is not fired without user input
                    var isSpinnerInitialized = false

                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (isSpinnerInitialized) {
                            videoCodecs = MeetingSDK.getSupportedVideoSendResolutions(videoDevices[position])

                            createSpinnerAdapter(videoCodecs)?.let {
                                videoCodecSpinner.adapter = it
                                (binding.meetingToolsLayout.videoCodecSpinner as? AutoCompleteTextView)?.setAdapter(it)
                            }
                        } else {
                            isSpinnerInitialized = true
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) { /* null */ }
                }

                enableInputsButton.setOnClickListener {
                    // Disable Spinners
                    audioInputSpinner.isEnabled = false
                    audioOutputSpinner.isEnabled = false
                    videoInputSpinner.isEnabled = false
                    videoCodecSpinner.isEnabled = false

                    enableInputsButton.startAnimation()

                    activity?.let {
                        if (!isAudioEnabled) {
                            // Check if RECORD_AUDIO permission has been given
                            // If not, request permission
                            if (ContextCompat.checkSelfPermission(it, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                launchLocalAudio()
                            }
                        }

                        if (!isVideoEnabled) {
                            // Check if CAMERA permission has been given
                            // If not, request permission
                            if (ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                recordVideoPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                launchLocalVideo()
                            }
                        }

                        // Both Audio & Video have been enabled, OK to display meeting
                        if (isAudioEnabled && isVideoEnabled) {
                            displayMeeting()
                        } else {
                            // Re-enable 'Enable' button
                            enableInputsButton.isEnabled = true
                        }

                        enableInputsButton.revertAnimation()
                    }
                }
            }

            toggleBottomSheetIcon.setOnClickListener {
                if (meetingToolsBottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    toggleBottomSheetIcon.visibility = View.GONE
                    meetingToolsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        }
    }

    /**
     * Helper Function
     * Sets up both the Video Grid RecyclerView and the Attendee List RecyclerView
     */
    private fun setupRecyclerAdapters() {
        binding.apply {
            videoGridLayout.apply {
                layoutManager = GridLayoutManager(context, SPAN_COUNT)
                setHasFixedSize(false)

                adapter = VideoRecyclerViewAdapter(videoStreamList, activity)
            }

            participantsLayout.participantRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                setHasFixedSize(true)
                addItemDecoration(DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL))

                adapter = ParticipantRecyclerViewAdapter(participantList)
            }

            networkCameraManagerLayout.networkCameraRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                setHasFixedSize(true)
                addItemDecoration(DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL))

                adapter = NetworkCameraRecyclerViewAdapter(networkCameraList, this@MeetingFragment)
            }
        }
    }

    /**
     * Helper Function
     * Sets up all of the listeners for the icons within the Meeting Tools Bottom Sheet
     */
    private fun setupBottomSheetIcons() {
        binding.meetingToolsLayout.apply {
            toggleAudioIcon.setOnClickListener {
                // Toggle audio device currently being selected in spinner
                toggleLocalAudioOutput(audioOutputSpinner.text.toString(), toggleAudioIcon.isChecked)
            }

            toggleMicIcon.setOnClickListener {
                // Toggle audio device currently being selected in spinner
                toggleLocalAudioInput(audioInputSpinner.text.toString(), toggleMicIcon.isChecked)

                /**
                 * Since the local participant does not have an audioStreamId, this button will also
                 * display the mute icon for the local participant
                 */
                videoStreamList.forEachIndexed { index, stream ->
                    if (stream.participant.isLocal) {
                        if (toggleMicIcon.isChecked) {
                            binding.videoGridLayout.adapter?.notifyItemChanged(index, INotificationCases.PARTICIPANT_UNMUTED)
                        } else {
                            binding.videoGridLayout.adapter?.notifyItemChanged(index, INotificationCases.PARTICIPANT_MUTED)
                        }
                    }
                }
            }

            toggleVideoIcon.setOnClickListener {
                // Toggle video device currently being selected in spinner
                toggleLocalVideo(
                    videoDevicesSpinner.text.toString(),
                    videoCodecSpinner.text.toString(),
                    isEnabled = toggleVideoIcon.isChecked
                )
            }

            endMeetingIcon.setOnClickListener { exitMeeting() }

            // Expand/collapse bottom sheet
            callSettingsIcon.setOnClickListener {
                when (meetingToolsBottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_EXPANDED ->
                        meetingToolsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    BottomSheetBehavior.STATE_COLLAPSED ->
                        meetingToolsBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    else -> meetingToolsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }

            /**
             * Listener for changes to the Bottom Sheet state
             * Used to provide extra screen space and show toggle icon
             */
            meetingToolsBottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> if (participantBottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                            binding.toggleBottomSheetIcon.visibility = View.VISIBLE
                        }
                        else -> { /* null */ }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) { }
            })
        }
    }

    /**
     * Helper Function
     * Sets up all of the listeners for the inputs within the expanded view of the Meeting Tools Bottom Sheet
     */
    private fun setupBottomSheetCallSettings() {
        binding.meetingToolsLayout.apply {
            videoDevicesSpinner.setOnItemClickListener { _, _, position, _ ->
                // Enables new local video with selected device
                switchCamera(videoDevices[position])
            }

            videoCodecSpinner.setOnItemClickListener { _, _, position, _ ->
                // Enables new local video with selected codec
                changeVideoResolution(videoCodecs[position])
            }

            screenCaptureButton.setOnCheckedChangeListener { _, isChecked ->
                toggleScreenCapture(isChecked)
            }

            manageNetworkCameraButton.setOnClickListener {
                meetingToolsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                networkCameraBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }

            audioInputSpinner.setOnItemClickListener { _, _, position, _ ->
                // Disables current local audio input
                toggleLocalAudioInput(isEnabled = false)

                // Enables new local audio input with selected device
                toggleLocalAudioInput(audioInputDevices[position], true)
            }

            audioOutputSpinner.setOnItemClickListener { _, _, position, _ ->
                // Disables current local audio output
                toggleLocalAudioOutput(isEnabled = false)

                // Enables new local audio output with selected device
                toggleLocalAudioOutput(audioOutputDevices[position], true)
            }
        }
    }

    /**
     * Helper Function
     * Sets up all of the listeners for the inputs within the Attendee List Bottom Sheet
     */
    private fun setupParticipantBottomSheet() {
        // Initial Bottom Sheet state
        participantBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        binding.participantsLayout.apply {
            closeBtn.setOnClickListener {
                participantBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }

            // Hide and show the Meeting Tools toggle icon
            participantBottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            binding.toggleBottomSheetIcon.visibility = View.VISIBLE

                            if (meetingToolsBottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN &&
                                binding.meetingToolsLayout.root.visibility == View.VISIBLE) {

                                binding.toggleBottomSheetIcon.visibility = View.VISIBLE
                            } else {
                                binding.toggleBottomSheetIcon.visibility = View.GONE
                            }
                        }
                        else -> binding.toggleBottomSheetIcon.visibility = View.GONE
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) { }
            })
        }
    }

    /**
     * Helper Function
     * Sets up all of the listeners for the inputs within the Network Camera Bottom Sheet
     */
    private fun setupNetworkCameraBottomSheet() {
        // Initial Bottom Sheet state
        networkCameraBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        with(binding.networkCameraManagerLayout) {
            closeBtn.setOnClickListener {
                networkCameraBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }

            // Add a new network camera to the list and automatically enable it
            addNetworkCameraButton.setOnClickListener {
                val newNetworkCameraDisplayName = if (addNetworkCameraDisplayTextField.editText?.text.toString().isNotBlank()) {
                    addNetworkCameraDisplayTextField.editText?.text.toString()
                } else null
                val newNetworkCameraURL = addNetworkCameraUrlTextField.editText?.text.toString()

                val newNetworkCameraItem = NetworkCameraItem(
                    newNetworkCameraDisplayName,
                    newNetworkCameraURL
                )

                if (MeetingSDK.enableNetworkVideo(newNetworkCameraURL, MeetingSDK.VP8_LARGE_NAME, newNetworkCameraDisplayName)) {
                    // Add new camera item
                    networkCameraList.add(newNetworkCameraItem)
                    networkCameraRecyclerView.adapter?.notifyItemInserted(networkCameraList.size - 1)

                    // Clear current text
                    addNetworkCameraDisplayTextField.editText?.setText("")
                    addNetworkCameraUrlTextField.editText?.setText("")
                } else {
                    addNetworkCameraUrlTextField.editText?.error = getString(R.string.unable_network_camera)
                }
            }

            // Clear all configured network cams
            clearNetworkCameraButton.setOnClickListener {
                networkCameraList.forEach { networkCameraItem ->
                    MeetingSDK.disableNetworkVideo(networkCameraItem.camURL)

                    val position = networkCameraList.indexOf(networkCameraItem)
                    networkCameraList.removeAt(position)
                    networkCameraRecyclerView.adapter?.notifyItemRemoved(position)
                }
            }
        }
    }

    /**
     * Helper Function
     * Hide Choose Devices dialog and display relevant Meeting Screen UI
     */
    private fun displayMeeting() {
        with(binding) {
            // Hide 'Choose Devices' card and show video displays
            chooseDevicesLayout.chooseDevicesCv.visibility = View.GONE
            enableInputsButton.visibility = View.GONE
            videoGridLayout.visibility = View.VISIBLE

            // Show Meeting Tools bottom sheet
            meetingToolsLayout.apply {
                root.visibility = View.VISIBLE
                meetingToolsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        MeetingSDK.exitMeeting()
    }

    /** ************ INotificationCallback Functions ************* */

    override fun participantAdded(p0: Participant?) {
        p0?.let { participant ->
            Log.d(TAG,"ms participantAdded ${participant.displayName}")
            // Do not add local participant or duplicate participant
            if (!participant.isLocal && participant !in participantList) {
                participantList.add(participant)
                binding.participantsLayout.participantRecyclerView.adapter?.notifyItemInserted(participantList.size - 1)

                // Automatically set participant volume
                MeetingSDK.setAudioStreamVolume(participant.audioInfo.streamId, STD_VOLUME)

                // Update number of attendees in participant bottom sheet title
                binding.participantsLayout.participantTitleTv.text = resources.getString(R.string.attendees_bottom_sheet_title, participantList.size)
            }
        }
    }

    override fun participantAudioAdded(p0: Participant?) {
        p0?.let { participant ->
            Log.d(TAG,"participantAudioAdded ${participant.displayName}")
        }
    }

    override fun onLogMessage(level: Int, message: String?) {
        if (message == null) return

        val tag = "TRACEMSG"
        when (level) {
            1 -> Log.e(tag, message)
            2 -> Log.w(tag, message)
            3 -> Log.i(tag, message)
            4, 5, 6, 7 -> Log.d(tag, message)
            else -> {}
        }
    }

    override fun participantVideoAdded(p0: Participant?, p1: String?) {
        p0?.let { participant ->
            Log.d(TAG,"participantVideoAdded ${participant.displayName}")
            p1?.let { streamId ->
                // Automatically enable video
                MeetingSDK.enableVideoStream(participant, streamId)
            }
        }
    }

    override fun participantVideoUpdated(p0: Participant?, p1: String?) {
        p0?.let { participant ->
            Log.d(TAG,"ms participantVideoUpdated: ${participant.displayName}")
        }
    }

    override fun participantVideoViewCreated(p0: Participant?, p1: VideoView?) {
        p0?.let { participant ->
            Log.d(TAG,"participantVideoViewCreated ${participant.displayName}")
            p1?.let { videoView ->
                val newStream = VideoStreamItem(participant = participant, videoView = videoView)
                if (participant.isLocal) {
                    // Adds local participant streams to first position in grid
                    videoStreamList.add(0, newStream)
                    binding.videoGridLayout.adapter?.notifyItemInserted(0)
                } else {
                    videoStreamList.add(newStream)
                    binding.videoGridLayout.adapter?.notifyItemInserted(videoStreamList.size - 1)
                }
            }
        }
    }

    override fun videoStreamBufferReady(p0: String?, p1: String?) {
        Log.d(TAG,"videoStreamBufferReady: $p0 $p1")
    }

    override fun videoFrameReady(p0: ByteArray?, p1: Int) {
        Log.d(TAG,"videoFrameReady: $p0 $p1")
    }

    override fun participantVideoRemoteLayoutChanged(p0: Participant?, p1: String?) {
        Log.d(TAG,"participantVideoRemoteLayoutChanged ${p0?.displayName} streamID: $p1")
    }

    override fun participantVideoRemoved(p0: Participant?, p1: String?) {
        if (p0 != null) {
            Log.d(TAG,"ms participantVideoRemoved ${p0.displayName}")
        }
        p1?.let { streamId ->
            val index = videoStreamList.indexOfFirst { it.videoView.streamId == streamId }

            if (index != -1) {
                videoStreamList.removeAt(index)
                binding.videoGridLayout.adapter?.notifyItemRemoved(index)
            }
        }
    }


    override fun participantRemoved(p0: Participant?) {
        p0?.let { participant ->
            // Remove all leftover streamIds
            Log.d(TAG,"ms participantVideoRemoved ${participant.displayName}")

            participant.videoInfo?.forEach { videoInfo ->
                val index = videoStreamList.indexOfFirst { it.videoView.streamId == videoInfo.streamId }

                if (index != -1) {
                    videoStreamList.removeAt(index)
                    binding.videoGridLayout.adapter?.notifyItemRemoved(index)
                }
            }

            // Update Attendee Bottom Sheet
            participantList.indexOf(participant).run {
                if (this != -1) {
                    participantList.removeAt(this)
                    binding.participantsLayout.participantRecyclerView.adapter?.notifyItemRemoved(this)

                    // Update number of attendees in participant bottom sheet title
                    binding.participantsLayout.participantTitleTv.text = resources.getString(R.string.attendees_bottom_sheet_title, participantList.size)
                }
            }
        }
    }

    private fun participantDidMute(p0: Participant?) {
        p0?.let { participant ->
            // Update Video Stream Bottom Sheet
            participant.videoInfo?.forEach { videoInfo ->
                val index = videoStreamList.indexOfFirst { it.videoView.streamId == videoInfo.streamId }
                if (index != -1) {
                    binding.videoGridLayout.adapter?.notifyItemChanged(index, INotificationCases.PARTICIPANT_MUTED)
                }
            }
            // Update Attendee Bottom Sheet
            participantList.indexOf(participant).run {
                binding.participantsLayout.participantRecyclerView.adapter?.notifyItemChanged(
                    this, INotificationCases.PARTICIPANT_MUTED
                )
            }
        }
    }

    private fun participantDidUnmute(p0: Participant?) {
        p0?.let { participant ->
            // Update Video Stream Bottom Sheet
            participant.videoInfo?.forEach { videoInfo ->
                val index = videoStreamList.indexOfFirst { it.videoView.streamId == videoInfo.streamId }
                if (index != -1) {
                    binding.videoGridLayout.adapter?.notifyItemChanged(index, INotificationCases.PARTICIPANT_UNMUTED)
                }
            }
            // Update Attendee Bottom Sheet
            participantList.indexOf(participant).run {
                binding.participantsLayout.participantRecyclerView.adapter?.notifyItemChanged(
                    this, INotificationCases.PARTICIPANT_UNMUTED
                )
            }
        }
    }

    override fun participantAmplitudeChanged(p0: Participant?, p1: Int, p2: Boolean) {
        println("Participant Amplitude Changed: $p0")
        if (p2) {
            participantDidMute(p0)
        } else {
            participantDidUnmute(p0)
        }
    }

    override fun inputMeterChanged(p0: Int) {
        println("Input Meter Changed: $p0")
    }

    override fun outputMeterChanged(p0: Int) {
        println("Output Meter Changed: $p0")
    }

    override fun binaryPlaybackEnded(p0: Long) {
        println("binaryPlaybackEnded: $p0")
    }

    override fun binaryPlaybackFailed(p0: Long) {
        println("binaryPlaybackfailed: $p0")
    }

    override fun videoError(errorName: String, errorDescription: String){
        Log.e(TAG,"videoError $errorName : $errorDescription")
        val currentCamera = binding.chooseDevicesLayout.videoInputSpinner.selectedItem.toString()
        val currentResolution = binding.chooseDevicesLayout.videoCodecSpinner.selectedItem.toString()
        if (currentCamera.equals(errorDescription)) {
            MeetingSDK.disableVideoCapture()
            showCameraError(currentCamera, currentResolution)
        }
    }

    override fun screenShareCancelled() {
        if (binding.meetingToolsLayout.screenCaptureButton.isChecked){
            binding.meetingToolsLayout.screenCaptureButton.isChecked=false
        }
    }

    fun showCameraError(currentCamera: String, currentResolution : String){
        activity?.let {
            AlertDialog.Builder(it).apply {
                setTitle(it.getString(R.string.camera_error))
                setMessage(it.getString(R.string.camera_error_desc))
                setPositiveButton(android.R.string.ok) { dialog, _ ->
                    // User clicked OK button
                    com.visionable.meetingsdk.MeetingSDK.enableVideoCapture(currentCamera, currentResolution)
                    dialog.dismiss()
                }
                setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                create()
                show()
            }
        }
    }
}
