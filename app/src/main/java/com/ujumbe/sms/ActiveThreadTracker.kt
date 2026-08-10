package com.ujumbe.sms

/**
 * Holds the phone number (cleaned) of whichever thread is currently visible on screen.
 * Set by ThreadActivity in onResume/onPause. Used by NotificationHelper to skip
 * showing a notification for a conversation the user is already looking at.
 */
object ActiveThreadTracker {
    @Volatile
    var activeThreadAddress: String? = null
}
