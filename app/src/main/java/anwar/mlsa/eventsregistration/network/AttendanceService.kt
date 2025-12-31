package anwar.mlsa.eventsregistration.network

import anwar.mlsa.eventsregistration.data.MarkAttendanceRequest
import anwar.mlsa.eventsregistration.data.MarkAttendanceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AttendanceService {
    @POST("attendance/mark")
    suspend fun markAttendance(@Body request: MarkAttendanceRequest): Response<MarkAttendanceResponse>
}
