package com.rupynow.application.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * DataStore preferences for storing user information like mobile number and email
 */
class UserPreferences(private val context: Context) {

    companion object {
        // Preference keys
        private val MOBILE_NUMBER_KEY = stringPreferencesKey("mobile_number")
        private val EMAIL_KEY = stringPreferencesKey("email")
    }

    /**
     * Get the stored mobile number
     */
    val mobileNumber: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[MOBILE_NUMBER_KEY]
    }

    /**
     * Get the stored email
     */
    val email: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[EMAIL_KEY]
    }

    /**
     * Save mobile number to DataStore
     */
    suspend fun saveMobileNumber(mobileNumber: String) {
        context.dataStore.edit { preferences ->
            preferences[MOBILE_NUMBER_KEY] = mobileNumber
        }
    }

    /**
     * Save email to DataStore
     */
    suspend fun saveEmail(email: String) {
        context.dataStore.edit { preferences ->
            preferences[EMAIL_KEY] = email
        }
    }

    /**
     * Save both mobile number and email
     */
    suspend fun saveUserInfo(mobileNumber: String, email: String) {
        context.dataStore.edit { preferences ->
            preferences[MOBILE_NUMBER_KEY] = mobileNumber
            preferences[EMAIL_KEY] = email
        }
    }

    /**
     * Clear all user data
     */
    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.remove(MOBILE_NUMBER_KEY)
            preferences.remove(EMAIL_KEY)
        }
    }
} 