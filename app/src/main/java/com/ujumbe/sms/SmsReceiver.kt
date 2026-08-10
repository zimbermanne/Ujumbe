package com.ujumbe.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_NEW_SMS = "com.ujumbe.sms.NEW_SMS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNotEmpty()) {
                val sender = messages[0].displayOriginatingAddress ?: ""
                val blockRepository = BlockRepository(context)

                // Check if sender is blocked
                if (blockRepository.isBlocked(sender)) {
                    Log.d("SmsReceiver", "Message from blocked sender $sender ignored.")
                    return
                }

                val fullBody = messages.joinToString("") { it.displayMessageBody ?: "" }
                Log.d("SmsReceiver", "Received SMS from $sender: $fullBody")

                // Send a local broadcast so any visible activities can refresh
                val localIntent = Intent(ACTION_NEW_SMS).apply {
                    putExtra("sender", sender)
                    putExtra("body", fullBody)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(localIntent)
            }
        }
    }
}
