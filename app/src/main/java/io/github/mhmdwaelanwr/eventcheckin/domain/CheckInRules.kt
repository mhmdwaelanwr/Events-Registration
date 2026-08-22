package io.github.mhmdwaelanwr.eventcheckin.domain

object CheckInRules {
    const val MAX_REGISTRATION_ID_LENGTH = 160
    const val MAX_PENDING_CHECK_INS = 100

    fun normalizeRegistrationId(value: String): String? {
        val normalized = value.trim()
        return normalized.takeIf {
            it.isNotEmpty() &&
                it.length <= MAX_REGISTRATION_ID_LENGTH &&
                it.none(Char::isISOControl)
        }
    }

    fun isDuplicateMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return DUPLICATE_MARKERS.any { marker ->
            message.contains(marker, ignoreCase = true)
        }
    }

    fun addPending(current: Set<String>, registrationId: String): Set<String>? {
        if (registrationId in current) return current
        if (current.size >= MAX_PENDING_CHECK_INS) return null
        return current + registrationId
    }

    fun shouldDebounceScan(
        registrationId: String,
        lastRegistrationId: String?,
        elapsedMillis: Long,
        debounceMillis: Long = 3_000L
    ): Boolean = registrationId == lastRegistrationId && elapsedMillis in 0 until debounceMillis

    fun shouldRemovePending(httpCode: Int, successfulBody: Boolean, message: String?): Boolean =
        successfulBody || httpCode == 409 || isDuplicateMessage(message)

    private val DUPLICATE_MARKERS = listOf(
        "already registered",
        "already checked in",
        "duplicate",
        "مسجل مسبقا",
        "تم تسجيله مسبقا"
    )
}
