package com.visionable.meetingrefapp.recyclerview.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.visionable.meetingrefapp.databinding.ParticipantListItemBinding
import com.visionable.meetingrefapp.fragments.MeetingFragment
import com.visionable.meetingsdk.MeetingSDK
import com.visionable.meetingsdk.Participant
import java.text.NumberFormat
import java.util.Locale

/**
 * Custom [RecyclerView.Adapter] to display the participants present in the call
 *
 * @property participantList -> [List<Participant>]: Full list of items to display
 */
class ParticipantRecyclerViewAdapter(
    private val participantList: List<Participant>
) : RecyclerView.Adapter<ParticipantRecyclerViewAdapter.ParticipantRecyclerViewHolder>() {

    companion object {
        private const val INITIAL_SLIDER_VOLUME = 50f
    }

    inner class ParticipantRecyclerViewHolder(
        val binding: ParticipantListItemBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ParticipantRecyclerViewHolder {
        return ParticipantRecyclerViewHolder(
            ParticipantListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    /**
     * Function used in order to not trigger the full re-rendering of the item through the normal
     * onBindViewHolder function
     *
     * @param holder
     * @param position
     * @param payloads -> List of payloads sent along with notifyItemChanged(index, payload)
     */
    override fun onBindViewHolder(
        holder: ParticipantRecyclerViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        for (payload in payloads) {
            when (payload) {
                MeetingFragment.Companion.INotificationCases.PARTICIPANT_MUTED ->
                    holder.binding.toggleAudioIcon.isChecked = false
                MeetingFragment.Companion.INotificationCases.PARTICIPANT_UNMUTED ->
                    holder.binding.toggleAudioIcon.isChecked = true
            }
        }
    }

    override fun onBindViewHolder(holder: ParticipantRecyclerViewHolder, position: Int) {
        val participant = participantList[position]

        holder.binding.apply {
            getNameAbbreviation(participant.siteName)?.let { abbvName ->
                if (abbvName.isNotBlank()) participantAbbvName.text = abbvName
            }

            participantName.text = participant.siteName

            toggleAudioIcon.isChecked = !participant.isMuted

            participantVolumeSlider.apply {
                value = INITIAL_SLIDER_VOLUME

                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) { /* null */ }

                    override fun onStopTrackingTouch(slider: Slider) {
                        setAudioStreamVolume(participant.audioStreamId, slider.value.toInt())
                    }
                })

                setLabelFormatter { value: Float ->
                    NumberFormat.getIntegerInstance(Locale.getDefault()).format(value.toInt())
                }
            }
        }
    }

    /**
     * Makes MeetingSDK call to set the participant's audio stream volume
     *
     * @param streamId -> [String]: Participant's audio stream ID
     * @param newVolume
     */
    private fun setAudioStreamVolume(streamId: String, newVolume: Int) {
        MeetingSDK.setAudioStreamVolume(streamId, newVolume)
    }

    override fun getItemCount(): Int {
        return participantList.size
    }

    private fun getNameAbbreviation(participantName: String): String? {
        val words: List<String> = participantName.split(" ")

        return when {
            words.size == 1 -> {
                when {
                    words[0].length > 1 -> words[0].substring(0..2)
                    words[0].length == 1 -> words[0][0].toString()
                    else -> null
                }
            }
            words.size > 1 -> "${words[0][0]}${words[1][0]}"
            else -> null
        }
    }
}
