package com.rupynow.application.config

object FirebaseConfig {
    // Replace these with your actual Firebase project values
    
    // Your Firebase Project ID (found in Project Settings)
    const val PROJECT_ID = "rupynow"
    
    // Your Analytics Measurement ID (found in Analytics > Data Streams)
    // If you can't find Measurement ID, use Stream ID or leave as empty for now
    const val MEASUREMENT_ID = "11467883633"  // Will be auto-detected from google-services.json
    
    // Your Cloud Messaging Sender ID (found in Project Settings)
    const val SENDER_ID = "215442487576"
    
    // Your Web Client ID (found in Authentication > Sign-in method > Google)
    const val WEB_CLIENT_ID = "your-web-client-id.apps.googleusercontent.com"
    
    // Notification Channel Configuration
    object NotificationChannels {
        const val MAIN_CHANNEL_ID = "rupynow_main_channel"
        const val MAIN_CHANNEL_NAME = "RupyNow Notifications"
        const val MAIN_CHANNEL_DESCRIPTION = "Main notifications for RupyNow app"
        
        const val PROMOTIONAL_CHANNEL_ID = "rupynow_promotional_channel"
        const val PROMOTIONAL_CHANNEL_NAME = "RupyNow Promotional"
        const val PROMOTIONAL_CHANNEL_DESCRIPTION = "Promotional notifications and offers"
        
        const val TRANSACTIONAL_CHANNEL_ID = "rupynow_transactional_channel"
        const val TRANSACTIONAL_CHANNEL_NAME = "RupyNow Transactional"
        const val TRANSACTIONAL_CHANNEL_DESCRIPTION = "Transaction confirmations and updates"
    }
    
    // Analytics Event Names
    object AnalyticsEvents {
        const val APP_OPEN = "app_open"
        const val PERMISSION_GRANTED = "permission_granted"
        const val PERMISSION_DENIED = "permission_denied"
        const val USER_REGISTRATION = "user_registration"
        const val BUTTON_CLICK = "button_click"
        const val FEATURE_USAGE = "feature_usage"
        const val CONVERSION = "conversion"
        const val USER_JOURNEY = "user_journey"
        const val SESSION_START = "session_start"
        const val SESSION_END = "session_end"
        const val APP_ERROR = "app_error"
        const val PERFORMANCE_METRIC = "performance_metric"
    }
    
    // Analytics Parameters
    object AnalyticsParams {
        const val PERMISSION_TYPE = "permission_type"
        const val EMAIL_PROVIDED = "email_provided"
        const val PHONE_PROVIDED = "phone_provided"
        const val REGISTRATION_COMPLETE = "registration_complete"
        const val BUTTON_NAME = "button_name"
        const val SCREEN_NAME = "screen_name"
        const val ERROR_TYPE = "error_type"
        const val ERROR_MESSAGE = "error_message"
        const val FEATURE_NAME = "feature_name"
        const val ACTION = "action"
        const val CONVERSION_TYPE = "conversion_type"
        const val VALUE = "value"
        const val CURRENCY = "currency"
        const val STEP = "step"
        const val STEP_NUMBER = "step_number"
        const val TOTAL_STEPS = "total_steps"
        const val METRIC_NAME = "metric_name"
        const val DURATION_SECONDS = "duration_seconds"
        const val TIMESTAMP = "timestamp"
    }
} 