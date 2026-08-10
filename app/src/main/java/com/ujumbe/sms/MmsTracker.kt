package com.ujumbe.sms

import android.content.Context

/**
 * Tracks that an MMS was received while MMS support is disabled/unimplemented.
 * This is deliberately lightweight - just a count and last-seen timestamp - so a
 * future screen (Settings, a banner, etc.) can tell the user "N picture messages
 * couldn't be shown" instead of messages just vanishing with no trace.
 */
object MmsTracker {
    private const val PREFS_NAME = "mms_tracker_prefs"
    private const val KEY_COUNT = "unsupported_mms_count"
    private const val KEY_LAST_SEEN = "unsupported_mms_last_seen"

    fun recordUnsupportedMmsSeen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit()
            .putInt(KEY_COUNT, count)
            .putLong(KEY_LAST_SEEN, System.currentTimeMillis())
            .apply()
    }

    fun getUnsupportedMmsCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_COUNT, 0)
    }

    fun getLastSeenTimestamp(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SEEN, 0L)
    }
}
