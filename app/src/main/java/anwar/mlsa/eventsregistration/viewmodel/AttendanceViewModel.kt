package anwar.mlsa.eventsregistration.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import anwar.mlsa.eventsregistration.Hedera
import anwar.mlsa.eventsregistration.data.MarkAttendanceRequest
import anwar.mlsa.eventsregistration.data.SettingsPreferences
import anwar.mlsa.eventsregistration.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        if (_uiState.value is AttendanceState.Loading) return

        val currentTime = System.currentTimeMillis()
        if (registrationId == lastScannedCode && (currentTime - lastScanTime) < SCAN_DELAY) {
            return
        }

        lastScannedCode = registrationId
        lastScanTime = currentTime

        viewModelScope.launch {
            _uiState.value = AttendanceState.Loading
            try {
                val context = getApplication<Application>().applicationContext
                val response = RetrofitClient.getInstance(context).markAttendance(MarkAttendanceRequest(registrationId))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        if (body.success) {
                            try {
                                withContext(Dispatchers.IO) {
                                    Hedera.submitRegistrationId(context, registrationId)
                                }
                                _uiState.value = AttendanceState.Success(
                                    message = body.message ?: "Attendance marked successfully",
                                    registrationId = registrationId
                                )
                            } catch (e: Exception) {
                                _uiState.value = AttendanceState.Error(
                                    "Hedera submit failed: ${e.localizedMessage}"
                                )
                            }
                        } else {
                            if (body.message?.contains("already registered", ignoreCase = true) == true ||
                                body.message?.contains("duplicate", ignoreCase = true) == true ||
                                body.message?.contains("مسجل مسبقا", ignoreCase = true) == true) {
                                _uiState.value = AttendanceState.AlreadyRegistered(
                                    message = body.message ?: "User already registered",
                                    registrationId = registrationId
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
                            registrationId = registrationId
                        )
                    } else {
                        _uiState.value = AttendanceState.Error("Error: ${response.code()} ${response.message()}")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = AttendanceState.Error("Connection error: ${e.localizedMessage}")
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
