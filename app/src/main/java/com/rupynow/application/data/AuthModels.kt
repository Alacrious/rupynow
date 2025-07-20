package com.rupynow.application.data

import com.google.gson.annotations.SerializedName

data class OtpGenerateRequest(
    @SerializedName("mobileNumber")
    val mobileNumber: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("deviceId")
    val deviceId: String
)

data class OtpGenerateResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: OtpGenerateData?,
    @SerializedName("errorCode")
    val errorCode: String?
)

data class OtpGenerateData(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("jwtToken")
    val jwtToken: String?,
    @SerializedName("mobileNumber")
    val mobileNumber: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("isNewUser")
    val isNewUser: Boolean
)

// OTP Verification Models
data class OtpVerifyRequest(
    @SerializedName("mobileNumber")
    val mobileNumber: String,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("otpCode")
    val otpCode: String,
    @SerializedName("deviceId")
    val deviceId: String
)

data class OtpVerifyResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: OtpVerifyData?,
    @SerializedName("errorCode")
    val errorCode: String?
)

data class OtpVerifyData(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("jwtToken")
    val jwtToken: String?,
    @SerializedName("mobileNumber")
    val mobileNumber: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("isNewUser")
    val isNewUser: Boolean?
) 