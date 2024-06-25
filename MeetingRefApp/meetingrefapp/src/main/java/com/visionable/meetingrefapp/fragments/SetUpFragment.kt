package com.visionable.meetingrefapp.fragments

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.visionable.meetingrefapp.R
import com.visionable.meetingrefapp.SdkListener
import com.visionable.meetingrefapp.databinding.SetupFragmentBinding
import com.visionable.meetingrefapp.persistence.MeetingPreferences
import com.visionable.meetingsdk.MeetingSDK
import com.visionable.meetingsdk.VisionableAPI

/**
 * Set Up Fragment to input all of the relevant information and to start the meeting
 * First screen in the flow
 */
class SetUpFragment : Fragment() {

    companion object {
        private val TAG = SetUpFragment::class.java.canonicalName
    }

    private var _binding: SetupFragmentBinding? = null
    private val binding get() = _binding!!

    private var sdkListener: SdkListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SetupFragmentBinding.inflate(inflater, container, false)
        setupBindingListeners()
        restoreMeetingInfo()
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
     * Initializes the meeting with the given server name and Meeting GUID, and provides a callback function on result
     * In this case, we are utilizing the callback function to update some UI elements
     *
     * @param participantName - Display name for the participant joining
     * @param server - URL of server hosting the meeting
     * @param guid - Meeting GUID that specifies which meeting to join within the given server
     */
    private fun initMeeting(participantName: String, server: String, guid: String) {
        // Disable button to prevent duplicate clicks
        binding.joinMeetingButton.apply {
            startAnimation()
            isEnabled = false
        }

        saveMeetingInfo()

        if (binding.joinMeetingLayout.meetingTokenCheckbox.isChecked) {

            // Use this code to authenticate with a hard coded id/password
            /*
            VisionableAPI.authenticate(server,"userID","password") { success, jwt ->
                if (success) {
                    VisionableAPI.initializeMeetingWithToken(server, guid, jwt) { result, server, token ->
                        onMeetingInitializedWithTokenAndJWT(result,server,guid,token,jwt,participantName)
                    }
                }
            }
*/
            // Use this code to join meeting as a guest
            VisionableAPI.initializeMeetingWithToken(server, guid, null) { result, server, token ->
                onMeetingInitializedWithToken(result, server, guid, token, participantName)
            }
        } else {
            VisionableAPI.initializeMeeting(server, guid) { result, v2resolvedServer, key ->
                onMeetingInitialized(result,v2resolvedServer,guid,key,participantName)
            }
        }
    }

    private fun onMeetingInitialized(result: Boolean, server: String, meetingUUID: String, key: String, participantName: String) {
        with(binding) {
            if (result) {
                sdkListener?.joinMeeting(server,meetingUUID,key,participantName)
            } else {
                // Show error modal & set click listener to null to avoid crash
                joinMeetingButton.apply {
                    revertAnimation()
                    backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.dark_grey, null))
                }

                sdkListener?.showErrorModal(
                    title = R.string.join_meeting_alert_title,
                    message = R.string.init_meeting_alert_message
                )
            }
        }
    }

    private fun onMeetingInitializedWithToken(result: Boolean, server: String, meetingUUID: String, token: String, participantName: String) {
        with(binding) {
            if (result) {
                sdkListener?.joinMeetingWithToken(server,meetingUUID,token,participantName)
            } else {
                // Show error modal & set click listener to null to avoid crash
                joinMeetingButton.apply {
                    revertAnimation()
                    backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.dark_grey, null))
                }

                sdkListener?.showErrorModal(
                    title = R.string.join_meeting_alert_title,
                    message = R.string.init_meeting_alert_message
                )
            }
        }
    }

    private fun onMeetingInitializedWithTokenAndJWT(result: Boolean, server: String, meetingUUID: String, token: String, jwt: String, participantName: String) {
        with(binding) {
            if (result) {
                sdkListener?.joinMeetingWithTokenAndJWT(server,meetingUUID,token,jwt,participantName)
            } else {
                // Show error modal & set click listener to null to avoid crash
                joinMeetingButton.apply {
                    revertAnimation()
                    backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.dark_grey, null))
                }

                sdkListener?.showErrorModal(
                    title = R.string.join_meeting_alert_title,
                    message = R.string.init_meeting_alert_message
                )
            }
        }
    }
    /**
     * Helper Function
     * Sets up binding click listeners for all of the relevant inputs
     */
    private fun setupBindingListeners() {
        binding.apply {
            joinMeetingButton.setOnClickListener {
                val participantName = joinMeetingLayout.participantNameInput
                val serverName = joinMeetingLayout.serverNameInput
                val guid = joinMeetingLayout.meetingGuidInput

                when {
                    participantName.editText?.text.isNullOrBlank() ->
                        joinMeetingLayout.participantNameInput.error = resources.getString(R.string.empty_participant_name_error)
                    serverName.editText?.text.isNullOrBlank() ->
                        joinMeetingLayout.participantNameInput.error = resources.getString(R.string.empty_server_name_error)
                    guid.editText?.text.isNullOrBlank() ->
                        joinMeetingLayout.participantNameInput.error = resources.getString(R.string.empty_meeting_guid_error)
                    else ->
                        initMeeting(
                            participantName.editText?.text.toString().trim(),
                            serverName.editText?.text.toString().trim(),
                            guid.editText?.text.toString().trim()
                        )
                }
            }
        }
    }

    private fun saveMeetingInfo() {
        val meetingPreferences = MeetingPreferences(requireContext())
        binding.joinMeetingLayout.apply {
            meetingPreferences.setParticipantName(participantNameInput.editText?.text.toString())
            meetingPreferences.setServerName(serverNameInput.editText?.text.toString())
            meetingPreferences.setUUID(meetingGuidInput.editText?.text.toString())
        }
    }

    private fun restoreMeetingInfo() {
        val meetingPreferences = MeetingPreferences(requireContext())
        binding.joinMeetingLayout.apply {
            participantNameInput.editText?.setText(meetingPreferences.getParticipantName())
            serverNameInput.editText?.setText(meetingPreferences.getServerName())
            meetingGuidInput.editText?.setText(meetingPreferences.getUUID())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
