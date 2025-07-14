package com.rupynow.application.network

import com.rupynow.application.data.SmsMessage
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SmsApi {
    @POST("post") // Using httpbin.org/post for testing
    suspend fun syncSms(@Body messages: List<SmsMessage>): Response<Unit>
} 