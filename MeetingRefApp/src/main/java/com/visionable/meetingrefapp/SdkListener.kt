package com.visionable.meetingrefapp

/**
 * Interface to connect Fragment UI events to SDK calls in the Activity
 */
interface SdkListener {

    fun joinMeeting(participantName: String)

    fun showErrorModal(title: Int? = null, message: Int? = null)
}