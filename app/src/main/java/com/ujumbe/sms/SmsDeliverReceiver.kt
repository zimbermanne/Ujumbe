package com.ujumbe.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val blockRepository = BlockRepository(context)
        val sender = messages[0].displayOriginatingAddress ?: ""

        // Write incoming messages to the SMS content provider (inbox)
        for (msg in messages) {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, msg.originatingAddress)
                put(Telephony.Sms.BODY, msg.messageBody)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            try {
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            } catch (e: Exception) {
                Log.e("SmsDeliverReceiver", "Error inserting message to inbox provider", e)
            }
        }

        // Check if the sender is blocked
        if (blockRepository.isBlocked(sender)) {
            Log.d("SmsDeliverReceiver", "Message from blocked sender $sender. Saved to provider but ignoring broadcast/notification.")
            return
        }

        val fullBody = messages.joinToString("") { it.displayMessageBody ?: "" }
        Log.d("SmsDeliverReceiver", "Received SMS from $sender: $fullBody")

        // Trigger existing UI-refresh logic (local broadcast)
        val localIntent = Intent(SmsReceiver.ACTION_NEW_SMS).apply {
            putExtra("sender", sender)
            putExtra("body", fullBody)
            setPackage(context.packageName)
        }
        context.sendBroadcast(localIntent)

        // Show a system notification so the user knows a message arrived even if the app is backgrounded
        try {
            val threadId = Telephony.Threads.getOrCreateThreadId(context, sender).toString()
            NotificationHelper.createChannels(context)
            NotificationHelper.showNewMessageNotification(context, threadId, sender, fullBody)
        } catch (e: Exception) {
            Log.e("SmsDeliverReceiver", "Error showing notification", e)
        }

        // Show popup preview if enabled
        showPopupPreviewIfEnabled(context, sender, fullBody)
    }

    private fun showPopupPreviewIfEnabled(context: Context, sender: String, body: String) {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isPopupEnabled = prefs.getBoolean("enable_message_popup", false)

        if (isPopupEnabled && PermissionsHelper.canDrawOverlays(context)) {
            val popupIntent = Intent(context, MessagePopupService::class.java).apply {
                putExtra("sender", sender)
                putExtra("body", body)
            }
            context.startService(popupIntent)
        }
    }
}
