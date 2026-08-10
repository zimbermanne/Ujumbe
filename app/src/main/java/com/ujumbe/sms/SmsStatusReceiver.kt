package com.ujumbe.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

/**
 * Receives the sent-status callback SmsManager fires per message part. Because sendTextMessage
 * used to be called with null sentIntent/deliveryIntent, the app previously had no way to know
 * whether an outgoing SMS actually left the device. This receiver closes that gap for the
 * "sent" half (delivery reports are carrier-dependent and are not tracked here).
 */
class SmsStatusReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SMS_SENT = "com.ujumbe.sms.SMS_SENT"
        const val ACTION_SEND_RESULT = "com.ujumbe.sms.SEND_RESULT"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_SUCCESS = "success"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return

        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: ""
        val success = resultCode == Activity.RESULT_OK

        if (!success) {
            val reason = when (resultCode) {
                SmsManager.RESULT_ERROR_NO_SERVICE -> "no service"
                SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off"
                SmsManager.RESULT_ERROR_NULL_PDU -> "null PDU"
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic failure"
                else -> "error code $resultCode"
            }
            Log.e("SmsStatusReceiver", "Send to $address failed: $reason")
            NotificationHelper.showSendFailedNotification(context, address)
        }

        // Local broadcast so an open ThreadActivity can update its UI immediately
        val localIntent = Intent(ACTION_SEND_RESULT).apply {
            putExtra(EXTRA_ADDRESS, address)
            putExtra(EXTRA_SUCCESS, success)
            setPackage(context.packageName)
        }
        context.sendBroadcast(localIntent)
    }
}
