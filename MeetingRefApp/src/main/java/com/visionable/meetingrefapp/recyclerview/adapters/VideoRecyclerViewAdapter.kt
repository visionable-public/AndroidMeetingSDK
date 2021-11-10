package com.visionable.meetingrefapp.recyclerview.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.visionable.meetingrefapp.data.VideoStreamItem
import com.visionable.meetingrefapp.databinding.VideoStreamCardBinding
import com.visionable.meetingrefapp.fragments.MeetingFragment
import com.visionable.meetingrefapp.fragments.SiteInfoDialogFragment

/**
 * Custom [RecyclerView.Adapter] to display the video streams available for the call
 *
 * @property videoStreamList -> [List<VideoStreamItem>]: Full list of items to display
 * @property activity -> [FragmentActivity]: Used to display the Site Info Dialog (Context needed)
 */
class VideoRecyclerViewAdapter(
    private val videoStreamList: List<VideoStreamItem>,
    private val activity: FragmentActivity?
) : RecyclerView.Adapter<VideoRecyclerViewAdapter.VideoRecyclerViewHolder>() {

    init {
        /**
         * IMPORTANT: Adding this line will prevent the RecyclerView from recycling the video views
         * and shuffling them as you scroll
         */
        setHasStableIds(true)
    }

    inner class VideoRecyclerViewHolder(
        val binding: VideoStreamCardBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoRecyclerViewHolder {
        val itemView = VideoStreamCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        itemView.root.layoutParams.height = parent.measuredHeight / 4

        return VideoRecyclerViewHolder(itemView)
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
        holder: VideoRecyclerViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        // If no payloads, call normal onBindViewHolder function and re-render item
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        holder.binding.apply {
            for (payload in payloads) {
                when (payload) {
                    MeetingFragment.Companion.INotificationCases.PARTICIPANT_MUTED ->
                        videoStreamMuteIcon.visibility = View.VISIBLE
                    MeetingFragment.Companion.INotificationCases.PARTICIPANT_UNMUTED ->
                        videoStreamMuteIcon.visibility = View.GONE
                }
            }
        }
    }

    override fun onBindViewHolder(holder: VideoRecyclerViewHolder, position: Int) {
        with(videoStreamList[position]) {
            holder.binding.apply {
                // Determine whether the participant video has already been displayed
                if (!isInitialized) {
                    this@with.isInitialized = true
                    videoStreamContainer.addView(this@with.videoView)

                    videoStreamMuteIcon.visibility = if (participant.isMuted) View.VISIBLE else View.GONE
                }

                // Outside of if statement in order to constantly update `position`
                videoStreamInfoIcon.setOnClickListener {
                    activity?.let {
                        SiteInfoDialogFragment(this@with).show(
                            it.supportFragmentManager,
                            SiteInfoDialogFragment::class.java.canonicalName
                        )
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return videoStreamList.size
    }

    /**
     * Important for the item to have a unique item ID in order for the recycler item to not get recycled
     * as the user scrolls through multiple items
     *
     * @param position
     * @return
     */
    override fun getItemId(position: Int): Long {
        return videoStreamList[position].uuid.mostSignificantBits
    }

    /**
     * Important for the item to have a unique item view type in order for the recycler item to not get recycled
     * as the user scrolls through multiple items
     *
     * @param position
     * @return
     */
    override fun getItemViewType(position: Int): Int {
        return videoStreamList[position].uuid.mostSignificantBits.toInt()
    }
}
