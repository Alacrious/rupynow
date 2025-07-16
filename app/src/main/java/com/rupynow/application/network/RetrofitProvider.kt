package com.rupynow.application.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // For development/testing - replace with your actual API endpoint
    val smsApi: SmsApi = Retrofit.Builder()
        .baseUrl("https://httpbin.org/") // Mock endpoint for testing
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SmsApi::class.java)

    // Auth API with the actual backend server
    val authApi: AuthApi = Retrofit.Builder()
        .baseUrl("https://a81c6dab1db5.ngrok-free.app/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthApi::class.java)
} 