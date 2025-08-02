package com.rupynow.application.data

import kotlinx.coroutines.delay

interface KycRepository {
    suspend fun uploadAndVerify(spokenCode: String): KycResult<VerificationData>
    suspend fun generateLivenessCode(): KycResult<String>
    suspend fun checkCameraPermissions(): KycResult<Boolean>
    suspend fun checkAudioPermissions(): KycResult<Boolean>
}

class KycRepositoryImpl : KycRepository {
    
    override suspend fun uploadAndVerify(spokenCode: String): KycResult<VerificationData> {
        return try {
            // Simulate network delay
            delay(3000)
            
            // Simulate API call with random results
            val isSuccess = (0..3).random() == 0 // 25% success rate
            
            if (isSuccess) {
                KycResult.Success(
                    VerificationData(
                        isVerified = true,
                        confidence = 0.95f,
                        livenessScore = 0.98f,
                        faceMatchScore = 0.92f
                    )
                )
            } else {
                val errors = listOf(
                    KycError.VerificationError("Face not matched with Aadhaar photo"),
                    KycError.LivenessError("Liveness check failed"),
                    KycError.VerificationError("Face not clear, please ensure good lighting"),
                    KycError.NetworkError("Network connection issue")
                )
                KycResult.Error(errors.random())
            }
        } catch (e: Exception) {
            KycResult.Error(KycError.NetworkError("Network error: ${e.message}"))
        }
    }
    
    override suspend fun generateLivenessCode(): KycResult<String> {
        return try {
            val codes = listOf("1234", "5678", "ABCD", "EFGH", "9876", "5432")
            KycResult.Success(codes.random())
        } catch (e: Exception) {
            KycResult.Error(KycError.UnknownError("Failed to generate liveness code: ${e.message}"))
        }
    }
    
    override suspend fun checkCameraPermissions(): KycResult<Boolean> {
        return try {
            // In a real implementation, this would check actual permissions
            KycResult.Success(true)
        } catch (e: Exception) {
            KycResult.Error(KycError.PermissionError("Camera permission check failed: ${e.message}"))
        }
    }
    
    override suspend fun checkAudioPermissions(): KycResult<Boolean> {
        return try {
            // In a real implementation, this would check actual permissions
            KycResult.Success(true)
        } catch (e: Exception) {
            KycResult.Error(KycError.PermissionError("Audio permission check failed: ${e.message}"))
        }
    }
} 