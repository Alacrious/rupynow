package com.rupynow.application.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID

// DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * DataStore preferences for storing user information like mobile number, email, userId, and device_id
 */
class UserPreferences(private val context: Context) {

    companion object {
        // Preference keys
        private val MOBILE_NUMBER_KEY = stringPreferencesKey("mobile_number")
        private val EMAIL_KEY = stringPreferencesKey("email")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
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
     * Get the stored userId
     */
    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }

    /**
     * Get the stored device_id
     */
    val deviceId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DEVICE_ID_KEY]
    }

    /**
     * Get or create device_id using UUID converted to Base32
     * If device_id doesn't exist, creates a new UUID, converts to Base32, and saves it
     */
    suspend fun getOrCreateDeviceId(): String {
        val existingDeviceId = deviceId.first()
        return if (existingDeviceId != null) {
            existingDeviceId
        } else {
            val uuid = UUID.randomUUID()
            val base32DeviceId = uuidToBase32(uuid)
            saveDeviceId(base32DeviceId)
            base32DeviceId
        }
    }
    
    /**
     * Convert UUID to Base32 string (custom implementation)
     * @param uuid The UUID to convert
     * @return Base32 encoded string (without padding)
     */
    private fun uuidToBase32(uuid: UUID): String {
        val uuidString = uuid.toString().replace("-", "")
        val bytes = uuidString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        // Custom Base32 encoding (RFC 4648)
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        
        var i = 0
        while (i < bytes.size) {
            val b1 = bytes[i].toInt() and 0xFF
            val b2 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b3 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            val b4 = if (i + 3 < bytes.size) bytes[i + 3].toInt() and 0xFF else 0
            val b5 = if (i + 4 < bytes.size) bytes[i + 4].toInt() and 0xFF else 0
            
            result.append(base32Chars[b1 shr 3])
            result.append(base32Chars[((b1 and 0x07) shl 2) or (b2 shr 6)])
            result.append(base32Chars[(b2 shr 1) and 0x1F])
            result.append(base32Chars[((b2 and 0x01) shl 4) or (b3 shr 4)])
            result.append(base32Chars[((b3 and 0x0F) shl 1) or (b4 shr 7)])
            result.append(base32Chars[(b4 shr 2) and 0x1F])
            result.append(base32Chars[((b4 and 0x03) shl 3) or (b5 shr 5)])
            result.append(base32Chars[b5 and 0x1F])
            
            i += 5
        }
        
        return result.toString()
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
     * Save userId to DataStore
     */
    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
        }
    }

    /**
     * Save device_id to DataStore
     */
    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_ID_KEY] = deviceId
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
     * Save user info including userId
     */
    suspend fun saveUserInfo(mobileNumber: String, email: String, userId: String) {
        context.dataStore.edit { preferences ->
            preferences[MOBILE_NUMBER_KEY] = mobileNumber
            preferences[EMAIL_KEY] = email
            preferences[USER_ID_KEY] = userId
        }
    }

    /**
     * Save user info including userId and device_id
     */
    suspend fun saveUserInfo(mobileNumber: String, email: String, userId: String, deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[MOBILE_NUMBER_KEY] = mobileNumber
            preferences[EMAIL_KEY] = email
            preferences[USER_ID_KEY] = userId
            preferences[DEVICE_ID_KEY] = deviceId
        }
    }

    /**
     * Clear all user data
     */
    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.remove(MOBILE_NUMBER_KEY)
            preferences.remove(EMAIL_KEY)
            preferences.remove(USER_ID_KEY)
            preferences.remove(DEVICE_ID_KEY)
        }
    }
} 