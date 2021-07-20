package com.visionable.meetingrefapp.data

import com.visionable.meetingsdk.Participant
import com.visionable.meetingsdk.VideoView
import java.util.*

/**
 * Data class that stores all of the relevant content for the [VideoRecyclerViewAdapter]
 *
 * @property uuid -> [UUID]: UUID value to be able to easily retrieve the item
 * @property participant -> [Participant]: Participant associated with the video stream
 * @property videoStreamId -> [String]: Specific stream ID associated with this item
 */
data class VideoStreamItem(
    val uuid: UUID = UUID.randomUUID(),
    val participant: Participant,
    val videoView: VideoView,
    var isInitialized: Boolean = false
)
