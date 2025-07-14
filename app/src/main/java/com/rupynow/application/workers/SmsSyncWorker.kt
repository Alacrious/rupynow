package com.rupynow.application.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rupynow.application.data.SmsRepository
import com.rupynow.application.network.RetrofitProvider
import java.io.IOException

class SmsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repo = SmsRepository(context.contentResolver)
    private val api = RetrofitProvider.smsApi
    private val prefs = context.getSharedPreferences("sms_sync", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        return try {
            // 1) fetch only new messages since last run:
            val lastRun = prefs.getLong("last_sync_ms", 0L)
            val msgs = if (lastRun == 0L) {
                repo.getReceivedSmsLastMonths(4)
            } else {
                repo.getReceivedSmsLastMonths(4).filter { it.dateMillis > lastRun }
            }

            if (msgs.isNotEmpty()) {
                val response = api.syncSms(msgs)      // POST to your backend
            }

            // 2) persist last-sync timestamp:
            prefs.edit().putLong("last_sync_ms", System.currentTimeMillis()).apply()
            
            Result.success()

        } catch (e: IOException) {
            // network or I/O issue → retry later
            Result.retry()
        } catch (e: SecurityException) {
            // lost permission → fail permanently
            Result.failure()
        } catch (e: Exception) {
            Result.failure()
        }
    }
} 