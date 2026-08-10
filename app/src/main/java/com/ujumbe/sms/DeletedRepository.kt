package com.ujumbe.sms

import android.content.Context
import android.content.SharedPreferences

class DeletedRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ujumbe_deletions", Context.MODE_PRIVATE)

    fun isThreadDeleted(threadId: String): Boolean {
        if (threadId.isEmpty()) return false
        return prefs.getBoolean("thread_$threadId", false)
    }

    fun deleteThread(threadId: String) {
        if (threadId.isEmpty()) return
        prefs.edit().putBoolean("thread_$threadId", true).apply()
    }

    fun isMessageDeleted(messageId: String): Boolean {
        if (messageId.isEmpty()) return false
        return prefs.getBoolean("msg_$messageId", false)
    }

    fun deleteMessage(messageId: String) {
        if (messageId.isEmpty()) return
        prefs.edit().putBoolean("msg_$messageId", true).apply()
    }

    fun clearAllDeletions() {
        prefs.edit().clear().apply()
    }
}
