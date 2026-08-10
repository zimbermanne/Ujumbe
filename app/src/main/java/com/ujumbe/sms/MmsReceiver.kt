package com.ujumbe.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * MMS/WAP push receiving is intentionally NOT implemented yet (parsing is non-trivial:
 * PDU decoding, attachment storage, etc.). This receiver stays registered only because
 * Android requires a WAP_PUSH_DELIVER receiver for default-SMS-app eligibility.
 *
 * When an MMS arrives we don't process or store it, but we do record that one was seen
 * so this isn't a silent black hole - see MmsTracker for the persisted count/timestamp,
 * which SettingsActivity (or any future screen) can surface to the user.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.w("MmsReceiver", "MMS received but not supported yet - message was not saved or shown.")
        MmsTracker.recordUnsupportedMmsSeen(context)
    }
}

