package com.ujumbe.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class RoleRequestHelper(private val activity: AppCompatActivity) {

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val granted = isDefaultSmsApp()
        onResult?.invoke(granted)
    }

    var onResult: ((Boolean) -> Unit)? = null

    fun isDefaultSmsApp(): Boolean =
        Telephony.Sms.getDefaultSmsPackage(activity) == activity.packageName

    fun requestDefaultSmsRole() {
        if (isDefaultSmsApp()) {
            onResult?.invoke(true)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            launcher.launch(intent)
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
            launcher.launch(intent)
        }
    }
}
