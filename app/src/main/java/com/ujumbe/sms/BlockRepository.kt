package com.ujumbe.sms

import android.content.Context
import android.content.SharedPreferences

class BlockRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ujumbe_blocks", Context.MODE_PRIVATE)

    fun isBlocked(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.cleanPhoneNumber()
        return prefs.getBoolean(cleanNumber, false)
    }

    fun setBlocked(phoneNumber: String, blocked: Boolean) {
        val cleanNumber = phoneNumber.cleanPhoneNumber()
        prefs.edit().putBoolean(cleanNumber, blocked).apply()
    }
}
