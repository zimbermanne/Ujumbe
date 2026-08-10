package com.ujumbe.sms

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    private var currentThemeColor: String? = null
    private var currentThemeMode: String? = null
    private var currentThemeFont: String? = null
    private var currentPackId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        currentThemeColor = prefs.getString("theme_color", "blue")
        currentThemeMode = prefs.getString("theme_mode", "light")
        currentThemeFont = prefs.getString("theme_font", "sans-serif")
        currentPackId = prefs.getString("active_pack_id", null)

        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        ThemeUtils.applyFont(this, findViewById(android.R.id.content))
        ThemeUtils.applyCustomPackSkin(this)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val newColor = prefs.getString("theme_color", "blue")
        val newMode = prefs.getString("theme_mode", "light")
        val newFont = prefs.getString("theme_font", "sans-serif")
        val newPackId = prefs.getString("active_pack_id", null)

        if (newColor != currentThemeColor || newMode != currentThemeMode ||
            newFont != currentThemeFont || newPackId != currentPackId
        ) {
            recreate()
        }
    }
}
