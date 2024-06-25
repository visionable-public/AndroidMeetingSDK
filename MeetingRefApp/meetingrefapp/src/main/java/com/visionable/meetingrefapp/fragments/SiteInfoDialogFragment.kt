package com.visionable.meetingrefapp.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import com.visionable.meetingrefapp.data.VideoStreamItem
import com.visionable.meetingrefapp.databinding.SiteInfoDialogBinding
import com.visionable.meetingsdk.MeetingSDK
import com.visionable.meetingsdk.ModeratorSDK
import com.visionable.meetingsdk.VideoInfo

/**
 * Site Info Dialog that displays all of the information for a specific
 *
 * @property participantInfo -> [VideoStreamItem]: Video stream information to populate dialog
 */
class SiteInfoDialogFragment(
    private val participantInfo: VideoStreamItem
) : DialogFragment() {

    private var _binding: SiteInfoDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = SiteInfoDialogBinding.inflate(LayoutInflater.from(context))
        setupViewBinding()

        return AlertDialog.Builder(requireActivity())
            .setView(binding.root)
            .create()
    }

    private fun setupViewBinding() {
        binding.apply {
            val streamId = participantInfo.videoView.streamId
            val videoInfo = participantInfo.participant.videoInfo
                .find { it.streamId == streamId } ?: VideoInfo(streamId)

            // Set Participant info
            siteTv.setText(videoInfo.siteName)
            deviceTv.setText(videoInfo.deviceName)
            emailTv.setText(participantInfo.participant.uuid)
            nameTv.setText(participantInfo.participant.displayName)
            // todo provide function to get codec from participant model
//            codecTv.setText()

            // Set Checkbox info
            activeCheckbox.isChecked = videoInfo.isActive
            localCheckbox.isChecked = participantInfo.participant.isLocal

            ModeratorSDK.sendPTZCommand(participantInfo.participant.uuid, "Android Front Camera","tilt_up");
            // Button Click Listeners
            closeBtn.setOnClickListener { dismiss() }

            enableStreamButton.apply {
                // Hide the button if participant is local
                if (participantInfo.participant.isLocal) visibility = View.GONE
                setOnClickListener {
                    MeetingSDK.enableVideoStream(participantInfo.participant, streamId)
                }
            }

            disableStreamButton.apply {
                // Hide the button if participant is local
                if (participantInfo.participant.isLocal) visibility = View.GONE
                setOnClickListener {
                    MeetingSDK.disableVideoStream(streamId)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
