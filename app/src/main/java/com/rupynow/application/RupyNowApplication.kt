package com.rupynow.application

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rupynow.application.workers.SmsSyncWorker
import java.util.concurrent.TimeUnit

class RupyNowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // SMS sync will be scheduled when permissions are granted
    }
} 