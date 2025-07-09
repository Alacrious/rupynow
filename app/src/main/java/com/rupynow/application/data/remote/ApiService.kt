package com.rupynow.application.data.remote

import com.rupynow.application.data.model.ApiResponse
import com.rupynow.application.data.model.UserVerificationRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    
    @POST("user/verify")
    suspend fun verifyUser(@Body request: UserVerificationRequest): ApiResponse
    
    // Add more API endpoints as needed
    // @GET("user/profile")
    // suspend fun getUserProfile(): UserProfileResponse
    
    // @POST("user/update")
    // suspend fun updateUser(@Body request: UpdateUserRequest): ApiResponse
} 