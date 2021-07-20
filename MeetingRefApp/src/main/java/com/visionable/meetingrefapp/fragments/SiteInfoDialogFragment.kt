package com.visionable.meetingrefapp.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import com.visionable.meetingsdk.MeetingSDK
import com.visionable.meetingrefapp.databinding.SiteInfoDialogBinding
import com.visionable.meetingrefapp.data.VideoStreamItem

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

            // Set Participant info
            with (participantInfo.participant) {
                siteTv.setText(this.siteName)
                deviceTv.setText(this.getDeviceName(streamId))
                emailTv.setText(this.email)
                nameTv.setText(this.getSiteName(streamId))
                codecTv.setText(this.getVideoCodec(streamId))
            }

            // Set Checkbox info
            activeCheckbox.isChecked = participantInfo.participant.isActive(streamId)
            localCheckbox.isChecked = participantInfo.participant.isLocal

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
                    MeetingSDK.disableVideoStream(participantInfo.participant, streamId)
                }
            }

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}