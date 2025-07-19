package com.rupynow.application.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

class SmsRetrieverBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsRetrieverBroadcastReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION == intent?.action) {
            val extras = intent.extras
            val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? Status
            
            when (status?.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                    
                    // Forward to the service for processing
                    val smsRetrieverService = SmsRetrieverService.getInstance()
                    smsRetrieverService.processSmsMessage(message)
                }
                CommonStatusCodes.TIMEOUT -> {
                    // Timeout - will restart automatically
                }
                else -> {
                    // Failed - will restart automatically
                }
            }
        }
    }
} 