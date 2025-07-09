package com.rupynow.application.domain.usecase

import com.rupynow.application.data.model.UserVerificationResult
import com.rupynow.application.data.repository.UserRepository
import javax.inject.Inject

class VerifyUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    
    suspend operator fun invoke(email: String, phone: String, deviceId: String? = null): UserVerificationResult {
        // Business logic validation
        if (email.isBlank() || phone.isBlank()) {
            return UserVerificationResult(
                isSuccess = false,
                message = "Email and phone number are required",
                errorCode = "VALIDATION_ERROR"
            )
        }
        
        if (!isValidEmail(email)) {
            return UserVerificationResult(
                isSuccess = false,
                message = "Please enter a valid email address",
                errorCode = "INVALID_EMAIL"
            )
        }
        
        if (!isValidPhone(phone)) {
            return UserVerificationResult(
                isSuccess = false,
                message = "Please enter a valid phone number",
                errorCode = "INVALID_PHONE"
            )
        }
        
        // Call repository
        return userRepository.verifyUser(email, phone, deviceId)
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    private fun isValidPhone(phone: String): Boolean {
        // Basic phone validation - can be enhanced based on requirements
        return phone.length >= 10 && phone.all { it.isDigit() || it in "+-() " }
    }
} 