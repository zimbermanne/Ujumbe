package com.ujumbe.sms

import android.content.Context
import android.content.SharedPreferences

class ArchiveRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ujumbe_archives", Context.MODE_PRIVATE)

    fun isArchived(threadId: String): Boolean {
        if (threadId.isEmpty()) return false
        return prefs.getBoolean(threadId, false)
    }

    fun setArchived(threadId: String, archived: Boolean) {
        if (threadId.isEmpty()) return
        prefs.edit().putBoolean(threadId, archived).apply()
    }

    fun getArchivedThreadIds(): Set<String> {
        return prefs.all.filterValues { it == true }.keys
    }
}
