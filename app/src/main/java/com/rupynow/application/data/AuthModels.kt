package com.rupynow.application.data

import com.google.gson.annotations.SerializedName

data class OtpGenerateRequest(
    @SerializedName("mobileNumber")
    val mobileNumber: String,
    @SerializedName("email")
    val email: String
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