package com.rupynow.application.services

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase
import com.rupynow.application.config.FirebaseConfig

class AnalyticsService private constructor(context: Context) {
    
    private val analytics: FirebaseAnalytics = Firebase.analytics
    
    init {
        // Firebase Analytics will auto-detect Measurement ID from google-services.json
        // No need to manually set it
    }
    
    companion object {
        @Volatile
        private var INSTANCE: AnalyticsService? = null
        
        fun getInstance(context: Context): AnalyticsService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnalyticsService(context).also { INSTANCE = it }
            }
        }
    }
    
    // User Properties
    fun setUserProperty(property: String, value: String) {
        analytics.setUserProperty(property, value)
    }
    
    fun setUserId(userId: String) {
        analytics.setUserId(userId)
    }
    
    // Screen Tracking
    fun logScreenView(screenName: String, screenClass: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            param(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        }
    }
    
    // Custom Events
    fun logAppOpen() {
        analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
    }
    
    fun logPermissionGranted(permissionType: String) {
        analytics.logEvent(FirebaseConfig.AnalyticsEvents.PERMISSION_GRANTED) {
            param(FirebaseConfig.AnalyticsParams.PERMISSION_TYPE, permissionType)
            param(FirebaseConfig.AnalyticsParams.TIMESTAMP, System.currentTimeMillis().toString())
        }
    }
    
    fun logPermissionDenied(permissionType: String) {
        analytics.logEvent(FirebaseConfig.AnalyticsEvents.PERMISSION_DENIED) {
            param(FirebaseConfig.AnalyticsParams.PERMISSION_TYPE, permissionType)
            param(FirebaseConfig.AnalyticsParams.TIMESTAMP, System.currentTimeMillis().toString())
        }
    }
    
    fun logUserRegistration(email: String, phone: String) {
        analytics.logEvent(FirebaseConfig.AnalyticsEvents.USER_REGISTRATION) {
            param(FirebaseConfig.AnalyticsParams.EMAIL_PROVIDED, if (email.isNotEmpty()) "yes" else "no")
            param(FirebaseConfig.AnalyticsParams.PHONE_PROVIDED, if (phone.isNotEmpty()) "yes" else "no")
            param(FirebaseConfig.AnalyticsParams.REGISTRATION_COMPLETE, "yes")
            param(FirebaseConfig.AnalyticsParams.TIMESTAMP, System.currentTimeMillis().toString())
        }
    }
    
    fun logButtonClick(buttonName: String, screenName: String) {
        analytics.logEvent(FirebaseConfig.AnalyticsEvents.BUTTON_CLICK) {
            param(FirebaseConfig.AnalyticsParams.BUTTON_NAME, buttonName)
            param(FirebaseConfig.AnalyticsParams.SCREEN_NAME, screenName)
            param(FirebaseConfig.AnalyticsParams.TIMESTAMP, System.currentTimeMillis().toString())
        }
    }
    
    fun logError(errorType: String, errorMessage: String) {
        analytics.logEvent("app_error") {
            param("error_type", errorType)
            param("error_message", errorMessage)
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
    
    fun logFeatureUsage(featureName: String, action: String) {
        analytics.logEvent("feature_usage") {
            param("feature_name", featureName)
            param("action", action)
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
    
    // Conversion Tracking
    fun logConversion(conversionType: String, value: Double = 0.0) {
        analytics.logEvent("conversion") {
            param("conversion_type", conversionType)
            param("value", value)
            param("currency", "USD")
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
    
    // User Journey Tracking
    fun logUserJourney(step: String, stepNumber: Int, totalSteps: Int) {
        analytics.logEvent("user_journey") {
            param("step", step)
            param("step_number", stepNumber.toLong())
            param("total_steps", totalSteps.toLong())
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
    
    // Performance Tracking
    fun logPerformanceMetric(metricName: String, value: Long) {
        analytics.logEvent("performance_metric") {
            param("metric_name", metricName)
            param("value", value)
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
    
    // Session Tracking
    fun logSessionStart() {
        analytics.logEvent("session_start") {
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
    
    fun logSessionEnd(duration: Long) {
        analytics.logEvent("session_end") {
            param("duration_seconds", duration)
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
    
    // API Call Tracking
    fun logApiCall(endpoint: String, status: String) {
        analytics.logEvent("api_call") {
            param("endpoint", endpoint)
            param("status", status)
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
    
    // OTP Detection Tracking
    fun logOtpDetected(otp: String, sender: String) {
        analytics.logEvent("otp_detected") {
            param("otp_length", otp.length.toLong())
            param("sender", sender)
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
    
    fun logOtpAutoFill(otp: String, screenName: String) {
        analytics.logEvent("otp_auto_fill") {
            param("otp_length", otp.length.toLong())
            param("screen_name", screenName)
            param("timestamp", System.currentTimeMillis().toString())
        }
    }
} 