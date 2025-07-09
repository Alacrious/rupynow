package com.rupynow.application.data.model

import com.google.gson.annotations.SerializedName

// API Request Models
data class UserVerificationRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("phone")
    val phone: String,
    @SerializedName("device_id")
    val deviceId: String? = null
)

// API Response Models
data class ApiResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: Any? = null,
    @SerializedName("error_code")
    val errorCode: String? = null
)

// Domain Models (for business logic)
data class UserVerificationResult(
    val isSuccess: Boolean,
    val message: String,
    val userId: String? = null,
    val errorCode: String? = null
) 