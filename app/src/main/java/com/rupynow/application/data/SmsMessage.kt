package com.rupynow.application.data

data class SmsMessage(
    val id: Long,
    val address: String?,
    val body: String?,
    val dateMillis: Long
) 