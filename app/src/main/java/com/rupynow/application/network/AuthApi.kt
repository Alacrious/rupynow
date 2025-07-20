package com.rupynow.application.network

import com.rupynow.application.data.OtpGenerateRequest
import com.rupynow.application.data.OtpGenerateResponse
import com.rupynow.application.data.OtpVerifyRequest
import com.rupynow.application.data.OtpVerifyResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    @POST("api/user/auth/otp/generate")
    suspend fun generateOtp(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: OtpGenerateRequest
    ): Response<OtpGenerateResponse>
    
    @POST("api/user/auth/otp/verify")
    suspend fun verifyOtp(
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: OtpVerifyRequest
    ): Response<OtpVerifyResponse>
} 