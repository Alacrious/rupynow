package com.rupynow.application.data

sealed class KycError {
    data class NetworkError(val message: String) : KycError()
    data class CameraError(val message: String) : KycError()
    data class PermissionError(val message: String) : KycError()
    data class VerificationError(val message: String) : KycError()
    data class LivenessError(val message: String) : KycError()
    data class UnknownError(val message: String) : KycError()
}

sealed class KycResult<out T> {
    data class Success<T>(val data: T) : KycResult<T>()
    data class Error(val error: KycError) : KycResult<Nothing>()
    object Loading : KycResult<Nothing>()
}

sealed class VerificationStatus {
    object Idle : VerificationStatus()
    object Loading : VerificationStatus()
    data class Success(val data: VerificationData) : VerificationStatus()
    data class Failure(val error: KycError) : VerificationStatus()
}

data class VerificationData(
    val isVerified: Boolean,
    val confidence: Float,
    val livenessScore: Float,
    val faceMatchScore: Float
) 