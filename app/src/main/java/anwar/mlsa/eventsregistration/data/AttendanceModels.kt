package anwar.mlsa.eventsregistration.data

data class MarkAttendanceRequest(
    val registrationId: String
)

data class MarkAttendanceResponse(
    val success: Boolean,
    val message: String? = null
)
