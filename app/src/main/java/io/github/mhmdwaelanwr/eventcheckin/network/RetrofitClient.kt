package io.github.mhmdwaelanwr.eventcheckin.network

import android.content.Context
import io.github.mhmdwaelanwr.eventcheckin.SecurityManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var instance: AttendanceService? = null

    private fun secureOkHttpClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun getInstance(context: Context): AttendanceService {
        return instance ?: synchronized(this) {
            instance ?: buildRetrofit(context).also { instance = it }
        }
    }

    private fun buildRetrofit(context: Context): AttendanceService {
        val baseUrl = SecurityManager.getConfig(context, "BASE_URL")
        require(baseUrl.startsWith("https://")) { "BASE_URL must use HTTPS" }
        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(secureOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AttendanceService::class.java)
    }
}
