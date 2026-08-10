package com.ujumbe.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "ujumbe_messages"
    private const val SENT_CHANNEL_ID = "ujumbe_send_status"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val incoming = NotificationChannel(
                CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new SMS messages"
                enableVibration(true)
            }

            val sendStatus = NotificationChannel(
                SENT_CHANNEL_ID,
                "Send status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when an outgoing message fails to send"
            }

            manager.createNotificationChannel(incoming)
            manager.createNotificationChannel(sendStatus)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showNewMessageNotification(context: Context, threadId: String, address: String, body: String) {
        if (!hasNotificationPermission(context)) return
        // Don't interrupt with a notification if this exact conversation is already open
        if (ActiveThreadTracker.activeThreadAddress != null &&
            ActiveThreadTracker.activeThreadAddress == address.cleanPhoneNumber()
        ) return

        val contactName = ContactsHelper.getContactName(context, address)

        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra("thread_id", threadId)
            putExtra("address", address)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            threadId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat_bubble_white)
            .setContentTitle(contactName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Stable id per-thread so repeated messages from the same person update in place
        manager.notify(threadId.hashCode(), notification)
    }

    fun cancelNotificationForThread(context: Context, threadId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(threadId.hashCode())
    }

    fun showSendFailedNotification(context: Context, address: String) {
        if (!hasNotificationPermission(context)) return
        val contactName = ContactsHelper.getContactName(context, address)

        val notification = NotificationCompat.Builder(context, SENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat_bubble_white)
            .setContentTitle("Message not sent")
            .setContentText("Your message to $contactName failed to send. Tap to retry.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((address + "_failed").hashCode(), notification)
    }
}
