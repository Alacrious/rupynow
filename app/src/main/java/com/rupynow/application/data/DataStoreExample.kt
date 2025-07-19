package com.rupynow.application.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Example usage of DataStore for storing mobile number and email
 * This class demonstrates how to use the UserRepository
 */
class DataStoreExample(private val context: Context) {
    
    private val userRepository = UserRepository(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Save user information (mobile number and email)
     */
    fun saveUserInfo(mobileNumber: String, email: String) {
        coroutineScope.launch {
            userRepository.saveUserInfo(mobileNumber, email)
        }
    }
    
    /**
     * Save only mobile number
     */
    fun saveMobileNumber(mobileNumber: String) {
        coroutineScope.launch {
            userRepository.saveMobileNumber(mobileNumber)
        }
    }
    
    /**
     * Save only email
     */
    fun saveEmail(email: String) {
        coroutineScope.launch {
            userRepository.saveEmail(email)
        }
    }
    
    /**
     * Get stored mobile number
     */
    fun getMobileNumber(onResult: (String?) -> Unit) {
        coroutineScope.launch {
            userRepository.getMobileNumber().collect { mobileNumber ->
                onResult(mobileNumber)
            }
        }
    }
    
    /**
     * Get stored email
     */
    fun getEmail(onResult: (String?) -> Unit) {
        coroutineScope.launch {
            userRepository.getEmail().collect { email ->
                onResult(email)
            }
        }
    }
    
    /**
     * Clear all stored user data
     */
    fun clearUserData() {
        coroutineScope.launch {
            userRepository.clearUserData()
        }
    }
} 