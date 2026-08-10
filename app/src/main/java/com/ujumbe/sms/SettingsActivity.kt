package com.ujumbe.sms

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit

@Suppress("DEPRECATION")
class SettingsActivity : BaseActivity() {

    private lateinit var blockRepository: BlockRepository
    private lateinit var smsRepository: SmsRepository

    private lateinit var switchAppLock: SwitchCompat
    private lateinit var switchDarkMode: SwitchCompat
    private lateinit var switchMessagePopup: SwitchCompat
    private lateinit var spinnerFontPicker: Spinner
    private lateinit var layoutBlockedContacts: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        blockRepository = BlockRepository(this)
        smsRepository = SmsRepository(this)

        val mmsSeenCount = MmsTracker.getUnsupportedMmsCount(this)
        if (mmsSeenCount > 0) {
            Toast.makeText(
                this,
                "$mmsSeenCount picture message(s) received but not shown - MMS support is coming soon.",
                Toast.LENGTH_LONG
            ).show()
        }

        val buttonBack: ImageButton = findViewById(R.id.buttonBack)
        switchAppLock = findViewById(R.id.switchAppLock)
        switchDarkMode = findViewById(R.id.switchDarkMode)
        switchMessagePopup = findViewById(R.id.switchMessagePopup)
        spinnerFontPicker = findViewById(R.id.spinnerFontPicker)
        layoutBlockedContacts = findViewById(R.id.layoutBlockedContacts)
        val layoutArchivedChats: View = findViewById(R.id.layoutArchivedChats)

        buttonBack.setOnClickListener { finish() }

        // Click listener to navigate to BlockedContactsActivity
        layoutBlockedContacts.setOnClickListener {
            val intent = android.content.Intent(this, BlockedContactsActivity::class.java)
            startActivity(intent)
        }

        // Click listener to navigate to ArchiveListActivity
        layoutArchivedChats.setOnClickListener {
            val intent = android.content.Intent(this, ArchiveListActivity::class.java)
            startActivity(intent)
        }

        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)

        // Set up App Lock toggle
        switchAppLock.isChecked = SecurityHelper.isAppLockEnabled(this)
        setupAppLockSwitchListener()

        // Set up Dark Mode toggle
        val isDark = prefs.getString("theme_mode", "light") == "dark"
        switchDarkMode.isChecked = isDark
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            val modeStr = if (isChecked) "dark" else "light"
            val currentMode = prefs.getString("theme_mode", "light")
            if (currentMode != modeStr) {
                prefs.edit { putString("theme_mode", modeStr) }
                recreate()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        // Manage / install themes
        findViewById<View>(R.id.layoutManageThemes).setOnClickListener {
            startActivity(android.content.Intent(this, ThemeSelectorActivity::class.java))
        }

        // Set up Font style picker
        setupFontPicker()

        // Set up Message Popup toggle
        setupMessagePopupToggle()
    }

    private fun setupMessagePopupToggle() {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        switchMessagePopup.isChecked = prefs.getBoolean("enable_message_popup", false)

        switchMessagePopup.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (PermissionsHelper.canDrawOverlays(this)) {
                    prefs.edit { putBoolean("enable_message_popup", true) }
                } else {
                    // Reset switch and request permission
                    switchMessagePopup.isChecked = false
                    Toast.makeText(this, "Please allow Ujumbe to display over other apps", Toast.LENGTH_LONG).show()
                    PermissionsHelper.requestOverlayPermission(this)
                }
            } else {
                prefs.edit { putBoolean("enable_message_popup", false) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission if the user just came back from settings
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        if (switchMessagePopup.isChecked && !PermissionsHelper.canDrawOverlays(this)) {
            switchMessagePopup.isChecked = false
            prefs.edit { putBoolean("enable_message_popup", false) }
        }
    }

    private fun setupAppLockSwitchListener() {
        switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != SecurityHelper.isAppLockEnabled(this)) {
                if (SecurityHelper.isAuthenticationAvailable(this)) {
                    SecurityHelper.authenticate(
                        activity = this,
                        title = if (isChecked) "Enable App Lock" else "Disable App Lock",
                        subtitle = "Confirm your identity",
                        onSuccess = {
                            SecurityHelper.setAppLockEnabled(this, isChecked)
                            Toast.makeText(this, if (isChecked) "App Lock enabled" else "App Lock disabled", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            switchAppLock.setOnCheckedChangeListener(null)
                            switchAppLock.isChecked = !isChecked
                            setupAppLockSwitchListener()
                            Toast.makeText(this, "Authentication failed: $error", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Toast.makeText(this, "Device screen lock PIN/Password or biometrics not set up.", Toast.LENGTH_LONG).show()
                    switchAppLock.setOnCheckedChangeListener(null)
                    switchAppLock.isChecked = false
                    setupAppLockSwitchListener()
                    SecurityHelper.setAppLockEnabled(this, false)
                }
            }
        }
    }

    private fun setupFontPicker() {
        val fontNames = arrayOf("Default (Sans-Serif)", "Classic (Serif)", "Developer (Monospace)", "Elegant (Cursive)", "Casual")
        val fontValues = arrayOf("sans-serif", "serif", "monospace", "cursive", "casual")

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFontPicker.adapter = spinnerAdapter

        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val currentFont = prefs.getString("theme_font", "sans-serif") ?: "sans-serif"
        val fontIndex = fontValues.indexOf(currentFont)
        if (fontIndex >= 0) {
            spinnerFontPicker.setSelection(fontIndex)
        }

        val isSpinnerInitialized = false
        spinnerFontPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isSpinnerInitialized) {
                    return
                }
                val selectedFont = fontValues[position]
                val activeFont = prefs.getString("theme_font", "sans-serif")
                if (selectedFont != activeFont) {
                    prefs.edit { putString("theme_font", selectedFont) }
                    recreate()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
