package com.rupynow.application.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing user data using DataStore
 */
class UserRepository(context: Context) {
    
    private val userPreferences = UserPreferences(context)
    
    /**
     * Get the stored mobile number as a Flow
     */
    fun getMobileNumber(): Flow<String?> = userPreferences.mobileNumber
    
    /**
     * Get the stored email as a Flow
     */
    fun getEmail(): Flow<String?> = userPreferences.email
    
    /**
     * Save mobile number
     */
    suspend fun saveMobileNumber(mobileNumber: String) {
        userPreferences.saveMobileNumber(mobileNumber)
    }
    
    /**
     * Save email
     */
    suspend fun saveEmail(email: String) {
        userPreferences.saveEmail(email)
    }
    
    /**
     * Save both mobile number and email
     */
    suspend fun saveUserInfo(mobileNumber: String, email: String) {
        userPreferences.saveUserInfo(mobileNumber, email)
    }
    
    /**
     * Clear all user data
     */
    suspend fun clearUserData() {
        userPreferences.clearUserData()
    }
} 