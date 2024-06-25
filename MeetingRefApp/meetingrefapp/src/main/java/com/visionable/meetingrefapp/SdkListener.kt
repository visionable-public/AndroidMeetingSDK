package com.visionable.meetingrefapp

/**
 * Interface to connect Fragment UI events to SDK calls in the Activity
 */
interface SdkListener {

    fun joinMeeting(server: String, meetingUUID: String, key: String?, participantName: String)
    fun joinMeetingWithToken(server: String, meetingUUID: String, token: String?, participantName: String)
    fun joinMeetingWithTokenAndJWT(server: String, meetingUUID: String, token: String, jwt: String, participantName: String)
    fun showErrorModal(title: Int? = null, message: Int? = null)
}
