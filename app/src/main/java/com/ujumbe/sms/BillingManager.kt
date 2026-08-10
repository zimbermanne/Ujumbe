package com.ujumbe.sms

import android.content.Context
import androidx.core.content.edit

/**
 * Handles In-App Purchases for Premium Themes.
 * Currently a mock implementation that stores purchase state in SharedPreferences.
 * In a production app, this would integrate with Google Play Billing Library.
 */
object BillingManager {
    private const val PREFS_NAME = "billing_prefs"
    private const val KEY_PURCHASED_PREFIX = "purchased_"

    fun isThemePurchased(context: Context, themeId: String): Boolean {
        // Built-in or non-premium themes are always "purchased"
        val pack = ThemePackManager.getPack(context, themeId)
        if (pack == null || !pack.isPremium) return true
        
        return prefs(context).getBoolean(KEY_PURCHASED_PREFIX + themeId, false)
    }

    /**
     * Simulates a successful purchase.
     * In production, this would be called after a successful callback from Google Play.
     */
    fun purchaseTheme(context: Context, themeId: String, onComplete: (Boolean) -> Unit) {
        // Simulate a short delay for the "payment processor"
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            prefs(context).edit {
                putBoolean(KEY_PURCHASED_PREFIX + themeId, true)
            }
            onComplete(true)
        }, 1500)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
