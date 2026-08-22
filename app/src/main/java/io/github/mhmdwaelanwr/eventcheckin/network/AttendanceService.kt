package io.github.mhmdwaelanwr.eventcheckin.network

import io.github.mhmdwaelanwr.eventcheckin.data.MarkAttendanceRequest
import io.github.mhmdwaelanwr.eventcheckin.data.MarkAttendanceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AttendanceService {
    @POST("attendance/mark")
    suspend fun markAttendance(@Body request: MarkAttendanceRequest): Response<MarkAttendanceResponse>
}
