package anwar.mlsa.eventsregistration.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anwar.mlsa.eventsregistration.data.MarkAttendanceRequest
import anwar.mlsa.eventsregistration.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AttendanceState {
    object Idle : AttendanceState()
    object Loading : AttendanceState()
    data class Success(val message: String, val registrationId: String) : AttendanceState()
    data class AlreadyRegistered(val message: String, val registrationId: String) : AttendanceState()
    data class Error(val message: String) : AttendanceState()
}

class AttendanceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AttendanceState>(AttendanceState.Idle)
    val uiState: StateFlow<AttendanceState> = _uiState.asStateFlow()

    private var lastScannedCode: String? = null
    private var lastScanTime: Long = 0

    companion object {
        private const val SCAN_DELAY = 3000L // Prevent rapid rescanning of the same code
    }

    fun markAttendance(registrationId: String) {
        if (_uiState.value is AttendanceState.Loading) return

        // Debounce same code scanning
        val currentTime = System.currentTimeMillis()
        if (registrationId == lastScannedCode && (currentTime - lastScanTime) < SCAN_DELAY) {
            return
        }

        lastScannedCode = registrationId
        lastScanTime = currentTime

        viewModelScope.launch {
            _uiState.value = AttendanceState.Loading
            try {
                val response = RetrofitClient.instance.markAttendance(MarkAttendanceRequest(registrationId))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        if (body.success) {
                             _uiState.value = AttendanceState.Success(
                                message = body.message ?: "Attendance marked successfully",
                                registrationId = registrationId
                            )
                        } else {
                            // Check for "already registered" keywords in the message
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
                    // Sometimes APIs return 400 or 409 for duplicates
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
