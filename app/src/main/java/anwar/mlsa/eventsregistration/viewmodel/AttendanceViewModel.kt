package anwar.mlsa.eventsregistration.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import anwar.mlsa.eventsregistration.Hedera
import anwar.mlsa.eventsregistration.SecurityManager
import anwar.mlsa.eventsregistration.data.MarkAttendanceRequest
import anwar.mlsa.eventsregistration.data.SettingsPreferences
import anwar.mlsa.eventsregistration.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class DarkModeConfig {
    SYSTEM, LIGHT, DARK
}

data class SettingsState(
    val darkMode: DarkModeConfig = DarkModeConfig.SYSTEM,
    val hapticEnabled: Boolean = true
)

sealed class AttendanceState {
    object Idle : AttendanceState()
    object Loading : AttendanceState()
    data class Success(val message: String, val registrationId: String) : AttendanceState()
    data class AlreadyRegistered(val message: String, val registrationId: String) : AttendanceState()
    data class PendingSync(val registrationId: String, val pendingCount: Int) : AttendanceState()
    data class Error(val message: String) : AttendanceState()
}

class AttendanceViewModel(
    application: Application,
    private val settingsPreferences: SettingsPreferences
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<AttendanceState>(AttendanceState.Idle)
    val uiState: StateFlow<AttendanceState> = _uiState.asStateFlow()

    private val _settingsState = MutableStateFlow(settingsPreferences.loadSettings())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private var lastScannedCode: String? = null
    private var lastScanTime: Long = 0

    companion object {
        private const val SCAN_DELAY = 3000L
        private const val MAX_REGISTRATION_ID_LENGTH = 160
    }

    fun updateDarkMode(config: DarkModeConfig) {
        _settingsState.value = _settingsState.value.copy(darkMode = config)
        settingsPreferences.saveDarkMode(config)
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        _settingsState.value = _settingsState.value.copy(hapticEnabled = enabled)
        settingsPreferences.saveHapticEnabled(enabled)
    }

    fun markAttendance(registrationId: String) {
        val normalizedId = registrationId.trim()
        if (normalizedId.isBlank() || normalizedId.length > MAX_REGISTRATION_ID_LENGTH ||
            normalizedId.any { it.isISOControl() }) {
            _uiState.value = AttendanceState.Error("Invalid registration code")
            return
        }
        if (_uiState.value is AttendanceState.Loading) return

        val currentTime = System.currentTimeMillis()
        if (normalizedId == lastScannedCode && (currentTime - lastScanTime) < SCAN_DELAY) {
            return
        }

        lastScannedCode = normalizedId
        lastScanTime = currentTime

        viewModelScope.launch {
            _uiState.value = AttendanceState.Loading
            try {
                val context = getApplication<Application>().applicationContext
                val response = RetrofitClient.getInstance(context).markAttendance(MarkAttendanceRequest(normalizedId))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        if (body.success) {
                            var successMessage = body.message ?: "Attendance marked successfully"
                            if (Hedera.isConfigured(context)) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        Hedera.submitRegistrationId(context, normalizedId)
                                    }
                                } catch (_: Exception) {
                                    // The attendance API is the source of truth. An optional audit
                                    // failure must not invite the operator to check the attendee in twice.
                                    successMessage += ". Audit trail unavailable"
                                }
                            }
                            _uiState.value = AttendanceState.Success(
                                message = successMessage,
                                registrationId = normalizedId
                            )
                            retryPendingCheckIns(excludeRegistrationId = normalizedId)
                        } else {
                            if (body.message?.contains("already registered", ignoreCase = true) == true ||
                                body.message?.contains("duplicate", ignoreCase = true) == true ||
                                body.message?.contains("مسجل مسبقا", ignoreCase = true) == true) {
                                _uiState.value = AttendanceState.AlreadyRegistered(
                                    message = body.message ?: "User already registered",
                                    registrationId = normalizedId
                                )
                            } else {
                                _uiState.value = AttendanceState.Error(body.message ?: "Failed to mark attendance")
                            }
                        }
                    } else {
                        _uiState.value = AttendanceState.Error("Empty response body")
                    }
                } else {
                    if (response.code() == 409) {
                         _uiState.value = AttendanceState.AlreadyRegistered(
                            message = "User already registered",
                            registrationId = normalizedId
                        )
                    } else {
                        _uiState.value = AttendanceState.Error("The check-in service rejected the request (${response.code()}).")
                    }
                }
            } catch (_: IOException) {
                val queued = SecurityManager.enqueuePendingCheckIn(
                    getApplication<Application>().applicationContext,
                    normalizedId
                )
                _uiState.value = if (queued) {
                    AttendanceState.PendingSync(
                        registrationId = normalizedId,
                        pendingCount = SecurityManager.getPendingCheckIns(
                            getApplication<Application>().applicationContext
                        ).size
                    )
                } else {
                    AttendanceState.Error("Offline queue is full. Connect to the internet and try again.")
                }
            } catch (_: Exception) {
                _uiState.value = AttendanceState.Error("Couldn't reach the check-in service. Check your connection and try again.")
            }
        }
    }

    fun retryPendingCheckIns(excludeRegistrationId: String? = null) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val pending = SecurityManager.getPendingCheckIns(context)
                .filterNot { it == excludeRegistrationId }
            if (pending.isEmpty()) return@launch

            try {
                val service = RetrofitClient.getInstance(context)
                for (registrationId in pending) {
                    val response = service.markAttendance(MarkAttendanceRequest(registrationId))
                    val accepted = response.isSuccessful && response.body()?.success == true
                    if (accepted || response.code() == 409) {
                        SecurityManager.removePendingCheckIn(context, registrationId)
                    }
                }
            } catch (_: Exception) {
                // Keep the encrypted queue intact and retry after the next online check-in/app start.
            }
        }
    }

    fun resetState() {
        _uiState.value = AttendanceState.Idle
    }
}

class AttendanceViewModelFactory(
    private val application: Application,
    private val settingsPreferences: SettingsPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AttendanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AttendanceViewModel(application, settingsPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
