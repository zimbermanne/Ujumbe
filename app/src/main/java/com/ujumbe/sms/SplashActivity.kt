package com.ujumbe.sms

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var roleRequestHelper: RoleRequestHelper

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        ThemeUtils.applyFont(this, findViewById(android.R.id.content))
        NotificationHelper.createChannels(this)

        roleRequestHelper = RoleRequestHelper(this).apply {
            onResult = { granted ->
                if (granted) {
                    handleSecurityAndLaunch()
                } else {
                    Toast.makeText(
                        this@SplashActivity,
                        "The app requires default SMS settings to access messages on Android 14. Proceeding in limited mode.",
                        Toast.LENGTH_LONG
                    ).show()
                    handleSecurityAndLaunch()
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            checkAndProceed()
        }, 1000)
    }

    private fun checkAndProceed() {
        if (PermissionsHelper.hasPermissions(this)) {
            checkDefaultSmsAndProceed()
        } else {
            PermissionsHelper.requestPermissions(this, PERMISSIONS_REQUEST_CODE)
        }
    }

    private fun checkDefaultSmsAndProceed() {
        if (roleRequestHelper.isDefaultSmsApp()) {
            handleSecurityAndLaunch()
        } else {
            roleRequestHelper.requestDefaultSmsRole()
        }
    }

    private fun handleSecurityAndLaunch() {
        if (SecurityHelper.isAppLockEnabled(this)) {
            SecurityHelper.authenticate(
                activity = this,
                onSuccess = {
                    launchHome()
                },
                onFailure = { error ->
                    Toast.makeText(this, "${getString(R.string.unlock_failed)}: $error", Toast.LENGTH_LONG).show()
                    // Allow retrying by clicking anywhere on the screen
                    findViewById<android.view.View>(android.R.id.content).setOnClickListener {
                        handleSecurityAndLaunch()
                    }
                }
            )
        } else {
            launchHome()
        }
    }

    private fun launchHome() {
        startActivity(Intent(this, ConversationListActivity::class.java))
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (PermissionsHelper.hasPermissions(this)) {
                checkDefaultSmsAndProceed()
            } else {
                Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
