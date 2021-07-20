package com.visionable.meetingrefapp.data

data class NetworkCameraItem(
	val displayName: String?,
	val camURL: String
) {
	override fun equals(other: Any?): Boolean {
		if (other !is NetworkCameraItem) return false

		return other.camURL == this.camURL
	}

	override fun hashCode(): Int {
		return camURL.hashCode()
	}
}