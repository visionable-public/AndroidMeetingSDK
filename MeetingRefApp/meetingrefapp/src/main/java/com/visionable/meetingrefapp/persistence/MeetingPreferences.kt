package com.visionable.meetingrefapp.persistence

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

class MeetingPreferences(context: Context) {

    companion object {
        private const val KEY_PARTICIPANT_NAME = "participant_name"
        private const val KEY_SERVER_NAME = "server_name"
        private const val KEY_UUID = "uuid"
        private const val DEFAULT_PARTICIPANT_NAME = "Pixel"
        private const val DEFAULT_SERVER_NAME = "v2.visionable.com"
        private const val DEFAULT_UUID = "6ef05350-89c0-4a5d-96e0-5462db124141"
    }

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun setParticipantName(participantName: String) {
        sharedPreferences
            .edit()
            .putString(KEY_PARTICIPANT_NAME, participantName)
            .apply()
    }

    fun setServerName(serverName: String) {
        sharedPreferences
            .edit()
            .putString(KEY_SERVER_NAME, serverName)
            .apply()
    }

    fun setUUID(uuid: String) {
        sharedPreferences
            .edit()
            .putString(KEY_UUID, uuid)
            .apply()
    }

    fun getParticipantName(): String = sharedPreferences.getString(KEY_PARTICIPANT_NAME, DEFAULT_PARTICIPANT_NAME) ?: DEFAULT_PARTICIPANT_NAME

    fun getServerName(): String = sharedPreferences.getString(KEY_SERVER_NAME, DEFAULT_SERVER_NAME) ?: DEFAULT_SERVER_NAME

    fun getUUID(): String = sharedPreferences.getString(KEY_UUID, DEFAULT_UUID) ?: DEFAULT_UUID

}