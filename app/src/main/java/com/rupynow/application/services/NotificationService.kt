package com.rupynow.application.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rupynow.application.MainActivity
import com.rupynow.application.R
import com.rupynow.application.config.FirebaseConfig

class NotificationService : FirebaseMessagingService() {
    
    companion object {
        private const val CHANNEL_ID = FirebaseConfig.NotificationChannels.MAIN_CHANNEL_ID
        private const val CHANNEL_NAME = FirebaseConfig.NotificationChannels.MAIN_CHANNEL_NAME
        private const val CHANNEL_DESCRIPTION = FirebaseConfig.NotificationChannels.MAIN_CHANNEL_DESCRIPTION
        
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableLights(true)
                    enableVibration(true)
                }
                
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }
        
        fun showLocalNotification(
            context: Context,
            title: String,
            message: String,
            notificationId: Int = 1
        ) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.notify(notificationId, notification)
        }
        
        fun scheduleNotification(
            context: Context,
            title: String,
            message: String,
            delayInMinutes: Long
        ) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(delayInMinutes, java.util.concurrent.TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
        }
        

    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to your server
        sendRegistrationToServer(token)
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Handle data message
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "RupyNow"
            val message = remoteMessage.data["message"] ?: "You have a new notification"
            showLocalNotification(this, title, message)
        }
        
        // Handle notification message
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "RupyNow"
            val body = notification.body ?: "You have a new notification"
            showLocalNotification(this, title, body)
        }
    }
    
    private fun sendRegistrationToServer(token: String) {
        // TODO: Send FCM token to your server
        // This is where you'd typically make an API call to your backend
    }
}

class NotificationWorker(
    context: Context,
    params: androidx.work.WorkerParameters
) : androidx.work.Worker(context, params) {
    
    override fun doWork(): androidx.work.ListenableWorker.Result {
        val title = inputData.getString("title") ?: "RupyNow"
        val message = inputData.getString("message") ?: "Time for your reminder!"
        
        NotificationService.showLocalNotification(applicationContext, title, message)
        
        return androidx.work.ListenableWorker.Result.success()
    }
} 