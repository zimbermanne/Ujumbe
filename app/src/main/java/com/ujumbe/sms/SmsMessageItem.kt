package com.ujumbe.sms

data class SmsMessageItem(
    val id: String,
    val threadId: String,
    val address: String,
    val body: String,
    val date: Long,
    val isSent: Boolean,
    val isUnread: Boolean = false
)
