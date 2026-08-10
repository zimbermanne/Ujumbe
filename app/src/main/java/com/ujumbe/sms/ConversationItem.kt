package com.ujumbe.sms

data class ConversationItem(
    val threadId: String,
    val address: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int = 0
)
