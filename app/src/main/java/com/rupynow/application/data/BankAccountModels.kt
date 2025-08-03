package com.rupynow.application.data

import com.google.gson.annotations.SerializedName

// Bank Account Verification Models
data class BankAccountVerificationRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("upiHandle")
    val upiHandle: String,
    @SerializedName("amount")
    val amount: Double = 1.0,
    @SerializedName("deviceId")
    val deviceId: String
)

data class BankAccountVerificationResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: BankAccountVerificationData?,
    @SerializedName("errorCode")
    val errorCode: String?
)

data class BankAccountVerificationData(
    @SerializedName("verificationId")
    val verificationId: String,
    @SerializedName("upiHandle")
    val upiHandle: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("expiresAt")
    val expiresAt: String
)

// Bank Account Details Models
data class BankAccountDetailsRequest(
    @SerializedName("verificationId")
    val verificationId: String,
    @SerializedName("userId")
    val userId: String
)

data class BankAccountDetailsResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: BankAccountDetailsData?,
    @SerializedName("errorCode")
    val errorCode: String?
)

data class BankAccountDetailsData(
    @SerializedName("accountHolderName")
    val accountHolderName: String,
    @SerializedName("accountNumber")
    val accountNumber: String,
    @SerializedName("ifscCode")
    val ifscCode: String,
    @SerializedName("bankName")
    val bankName: String,
    @SerializedName("isVerified")
    val isVerified: Boolean,
    @SerializedName("verificationTimestamp")
    val verificationTimestamp: String
)

// Bank Account Confirmation Models
data class BankAccountConfirmationRequest(
    @SerializedName("verificationId")
    val verificationId: String,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("isConfirmed")
    val isConfirmed: Boolean
)

data class BankAccountConfirmationResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: BankAccountConfirmationData?,
    @SerializedName("errorCode")
    val errorCode: String?
)

data class BankAccountConfirmationData(
    @SerializedName("confirmationId")
    val confirmationId: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("confirmedAt")
    val confirmedAt: String
)

// UI State Models
sealed class BankAccountVerificationState {
    object Initial : BankAccountVerificationState()
    object Loading : BankAccountVerificationState()
    data class UpiReady(val upiHandle: String, val verificationId: String) : BankAccountVerificationState()
    object WaitingForTransfer : BankAccountVerificationState()
    data class TransferDetected(val verificationId: String) : BankAccountVerificationState()
    data class AccountDetailsFetched(val details: BankAccountDetailsData) : BankAccountVerificationState()
    data class Confirmed(val confirmationId: String) : BankAccountVerificationState()
    data class Error(val message: String) : BankAccountVerificationState()
    object Timeout : BankAccountVerificationState()
} 