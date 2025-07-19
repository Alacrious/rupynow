package com.rupynow.application.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.util.Log
// Google Play Services imports for SMS Retriever API
// These will be resolved when the project is built
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.regex.Pattern
import com.rupynow.application.services.AnalyticsService

class SmsRetrieverService {
    companion object {
        private const val TAG = "SmsRetrieverService"
        
        @Volatile
        private var INSTANCE: SmsRetrieverService? = null
        
        fun getInstance(): SmsRetrieverService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmsRetrieverService().also { INSTANCE = it }
            }
        }
    }
    
    private val _detectedOtp = MutableStateFlow<String?>(null)
    val detectedOtp: StateFlow<String?> = _detectedOtp
    
    private var smsReceiver: BroadcastReceiver? = null
    private var isListening = false
    

    
    /**
     * Start listening for SMS with OTP using SMS Retriever API
     * This is Google's official way to automatically read OTPs
     */
    fun startListening(context: Context) {
        if (isListening) return
        
        try {
            // Register BroadcastReceiver for SMS retrieval
            smsReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (SmsRetriever.SMS_RETRIEVED_ACTION == intent?.action) {
                        val extras = intent.extras
                        val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? Status
                        
                        when (status?.statusCode) {
                            CommonStatusCodes.SUCCESS -> {
                                val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                                
                                // Extract OTP from the message
                                val otp = extractOtpFromMessage(message)
                                if (otp != null) {
                                    _detectedOtp.value = otp
                                    
                                    // Log analytics
                                    context?.let { ctx ->
                                        val analyticsService = AnalyticsService.getInstance(ctx)
                                        analyticsService.logOtpDetected(otp, "SMS_RETRIEVER")
                                    }
                                }
                            }
                            CommonStatusCodes.TIMEOUT -> {
                                // Restart listening after timeout
                                context?.let { ctx -> restartListening(ctx) }
                            }
                            else -> {
                                // Restart listening on failure
                                context?.let { ctx -> restartListening(ctx) }
                            }
                        }
                    }
                }
            }
            
            // Register the receiver
            val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
            context.registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            
            // Start SMS Retriever
            val smsRetrieverClient = SmsRetriever.getClient(context)
            val task = smsRetrieverClient.startSmsRetriever()
            
            task.addOnSuccessListener {
                isListening = true
            }.addOnFailureListener { exception ->
                isListening = false
            }
            
        } catch (e: Exception) {
            isListening = false
        }
    }
    
    /**
     * Stop listening for SMS
     */
    fun stopListening(context: Context) {
        try {
            smsReceiver?.let {
                context.unregisterReceiver(it)
            }
            smsReceiver = null
            isListening = false
        } catch (e: Exception) {
            // Ignore errors
        }
    }
    
    /**
     * Restart SMS listening after timeout or failure
     */
    private fun restartListening(context: Context) {
        stopListening(context)
        // Add a small delay before restarting
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startListening(context)
        }, 1000)
    }
    
    /**
     * Extract OTP from SMS message using regex patterns
     * The SMS should be formatted as: <#> Your OTP is 123456\nAppHash
     */
    private fun extractOtpFromMessage(message: String?): String? {
        if (message.isNullOrBlank()) return null
        
        // Common OTP patterns
        val otpPatterns = listOf(
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
            Pattern.compile("code[\\s]*is[\\s]*\\d{4,6}"), // code is 123456
            Pattern.compile("Your[\\s]*RupyNow[\\s]*code[\\s]*is[\\s]*\\d{4,6}"), // Your RupyNow code is 123456
            Pattern.compile("RupyNow[\\s]*code[\\s]*is[\\s]*\\d{4,6}") // RupyNow code is 123456
        )
        
        for (pattern in otpPatterns) {
            val matcher = pattern.matcher(message)
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
     * Clear the detected OTP
     */
    fun clearDetectedOtp() {
        _detectedOtp.value = null
    }
    
    /**
     * Get the last detected OTP
     */
    fun getLastDetectedOtp(): String? {
        return _detectedOtp.value
    }
    
    /**
     * Check if currently listening
     */
    fun isCurrentlyListening(): Boolean {
        return isListening
    }
    
    /**
     * Process SMS message from manifest receiver
     */
    fun processSmsMessage(message: String?) {
        // Extract OTP from the message
        val otp = extractOtpFromMessage(message)
        if (otp != null) {
            _detectedOtp.value = otp
        }
    }
} 