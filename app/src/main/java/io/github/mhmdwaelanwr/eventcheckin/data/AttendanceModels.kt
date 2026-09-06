package io.github.mhmdwaelanwr.eventcheckin.data

data class MarkAttendanceRequest(
    val registrationId: String,
    val sudo: Boolean = false
)

data class MarkAttendanceResponse(
    val success: Boolean,
    val error: String? = null,
    val message: String? = null,
    val registrationId: String? = null,
    val day: Int? = null,
    val alreadyMarked: Boolean? = null
)
