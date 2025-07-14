package com.rupynow.application.data

import android.content.ContentResolver
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

class SmsRepository(private val cr: ContentResolver) {

    suspend fun getReceivedSmsLastMonths(months: Long = 4): List<SmsMessage> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val window = Duration.ofDays(30 * months).toMillis()
            val uri = Telephony.Sms.Inbox.CONTENT_URI              // only received
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val sel = "${Telephony.Sms.DATE} >= ?"
            val args = arrayOf((now - window).toString())
            val sortOrder = "${Telephony.Sms.DATE} DESC"

            val out = mutableListOf<SmsMessage>()
            cr.query(uri, projection, sel, args, sortOrder)?.use { cursor ->
                val idxId = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val idxAddr = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val idxBody = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val idxDate = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                
                while (cursor.moveToNext()) {
                    out += SmsMessage(
                        id = cursor.getLong(idxId),
                        address = cursor.getString(idxAddr),
                        body = cursor.getString(idxBody),
                        dateMillis = cursor.getLong(idxDate)
                    )
                }
            }
            out
        }
} 