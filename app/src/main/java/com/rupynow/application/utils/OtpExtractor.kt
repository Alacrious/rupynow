package com.rupynow.application.utils

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.rupynow.application.data.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.Int

object OtpExtractor {
    private const val TAG = "OtpExtractor"
    
    // Common OTP patterns for different services
    private val OTP_PATTERNS = listOf(
        Pattern.compile("\\b\\d{4,6}\\b"), // 4-6 digit OTP
        Pattern.compile("OTP[\\s:]*\\d{4,6}"), // OTP: 123456
        Pattern.compile("code[\\s:]*\\d{4,6}"), // code: 123456
        Pattern.compile("verification[\\s:]*\\d{4,6}"), // verification: 123456
        Pattern.compile("\\d{4,6}[\\s]*is[\\s]*your[\\s]*OTP"), // 123456 is your OTP
        Pattern.compile("your[\\s]*OTP[\\s]*is[\\s]*\\d{4,6}"), // your OTP is 123456
        Pattern.compile("\\d{4,6}[\\s]*is[\\s]*your[\\s]*verification[\\s]*code"), // 123456 is your verification code
        Pattern.compile("verification[\\s]*code[\\s]*is[\\s]*\\d{4,6}"), // verification code is 123456
        Pattern.compile("\\d{4,6}[\\s]*is[\\s]*your[\\s]*PIN"), // 123456 is your PIN
        Pattern.compile("PIN[\\s:]*\\d{4,6}"), // PIN: 123456
        Pattern.compile("\\d{4,6}[\\s]*is[\\s]*your[\\s]*code"), // 123456 is your code
        Pattern.compile("code[\\s]*is[\\s]*\\d{4,6}") // code is 123456
    )
    
    // Keywords that indicate OTP messages
    private val OTP_KEYWORDS = listOf(
        "otp", "verification", "code", "verify", "authenticate", "pin", "security"
    )
    
    // Common sender patterns for OTP messages
    private val OTP_SENDER_PATTERNS = listOf(
        Pattern.compile(".*RUPY.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*BANK.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*PAY.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*VERIFY.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*OTP.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*CODE.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*SECURITY.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*AUTH.*", Pattern.CASE_INSENSITIVE)
    )
    
    /**
     * Scan recent SMS messages for OTPs
     * @param context Application context
     * @param phoneNumber Optional phone number to filter messages
     * @param maxMessages Maximum number of messages to scan
     * @return List of detected OTPs with their details
     */
    suspend fun scanRecentSmsForOtp(
        context: Context,
        phoneNumber: String? = null,
        maxMessages: Int = 50
    ): List<OtpResult> = withContext(Dispatchers.IO) {
        try {
            val smsRepository = SmsRepository(context.contentResolver)
            val messages = smsRepository.getReceivedSmsLastMonths(1) // Last month only
            
            val results = mutableListOf<OtpResult>()
            
            messages.take(maxMessages).forEach { smsMessage ->
                val sender = smsMessage.address ?: ""
                val body = smsMessage.body ?: ""
                
                // Skip if phone number filter is provided and doesn't match
                if (phoneNumber != null && !sender.contains(phoneNumber.takeLast(10))) {
                    return@forEach
                }
                
                // Check if this looks like an OTP message
                if (isOtpMessage(sender, body)) {
                    val otp = extractOtp(body)
                    if (otp != null) {
                        results.add(
                            OtpResult(
                                otp = otp,
                                sender = sender,
                                messageBody = body,
                                timestamp = smsMessage.dateMillis
                            )
                        )
                    }
                }
            }
            
            Log.d(TAG, "Found ${results.size} OTP messages")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning SMS for OTP", e)
            emptyList()
        }
    }
    
    /**
     * Check if a message looks like an OTP message
     */
    private fun isOtpMessage(sender: String, messageBody: String): Boolean {
        val body = messageBody.lowercase()
        
        // Check if sender matches OTP patterns
        val senderMatches = OTP_SENDER_PATTERNS.any { pattern ->
            pattern.matcher(sender).matches()
        }
        
        // Check if message body contains OTP-related keywords
        val bodyContainsOtpKeywords = OTP_KEYWORDS.any { keyword ->
            body.contains(keyword)
        }
        
        // Check if message contains a 4-6 digit number
        val containsOtpNumber = OTP_PATTERNS.any { pattern ->
            pattern.matcher(messageBody).find()
        }
        
        return senderMatches || (bodyContainsOtpKeywords && containsOtpNumber)
    }
    
    /**
     * Extract OTP from message body
     */
    private fun extractOtp(messageBody: String): String? {
        for (pattern in OTP_PATTERNS) {
            val matcher = pattern.matcher(messageBody)
            if (matcher.find()) {
                // Extract the actual digits
                val match = matcher.group()
                val digitsOnly = match.replace(Regex("[^0-9]"), "")
                
                // Validate it's 4-6 digits
                if (digitsOnly.length in 4..6) {
                    return digitsOnly
                }
            }
        }
        return null
    }
    
    /**
     * Get the most recent OTP from SMS
     */
    suspend fun getMostRecentOtp(context: Context, phoneNumber: String? = null): OtpResult? {
        val results = scanRecentSmsForOtp(context, phoneNumber, 20)
        return results.maxByOrNull { it.timestamp }
    }
    
    /**
     * Data class to hold OTP detection results
     */
    data class OtpResult(
        val otp: String,
        val sender: String,
        val messageBody: String,
        val timestamp: Long
    )
} 