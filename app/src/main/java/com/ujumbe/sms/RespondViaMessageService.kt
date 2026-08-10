package com.ujumbe.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val number = it.data?.schemeSpecificPart
            val message = it.getStringExtra(Intent.EXTRA_TEXT)
            if (!number.isNullOrEmpty() && !message.isNullOrEmpty()) {
                try {
                    val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                    smsManager.sendTextMessage(number, null, message, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
