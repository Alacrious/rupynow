package com.rupynow.application.data.repository

import com.rupynow.application.data.model.ApiResponse
import com.rupynow.application.data.model.UserVerificationRequest
import com.rupynow.application.data.model.UserVerificationResult
import com.rupynow.application.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val apiService: ApiService
) {
    
    suspend fun verifyUser(email: String, phone: String, deviceId: String? = null): UserVerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                val request = UserVerificationRequest(
                    email = email,
                    phone = phone,
                    deviceId = deviceId
                )
                
                val response = apiService.verifyUser(request)
                
                UserVerificationResult(
                    isSuccess = response.success,
                    message = response.message,
                    userId = response.data?.toString(),
                    errorCode = response.errorCode
                )
            } catch (e: Exception) {
                UserVerificationResult(
                    isSuccess = false,
                    message = "Network error: ${e.message}",
                    errorCode = "NETWORK_ERROR"
                )
            }
        }
    }
} 