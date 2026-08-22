package io.github.mhmdwaelanwr.eventcheckin.data

data class MarkAttendanceRequest(
    val registrationId: String
)

data class MarkAttendanceResponse(
    val success: Boolean,
    val message: String? = null
)
