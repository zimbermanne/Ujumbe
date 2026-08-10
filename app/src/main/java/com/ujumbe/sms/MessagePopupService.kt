package com.ujumbe.sms

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class MessagePopupService : Service() {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: "Unknown"
        val body = intent?.getStringExtra("body") ?: ""

        showPopup(sender, body)

        // Dismiss after 1.5 seconds
        handler.removeCallbacks(dismissRunnable)
        handler.postDelayed(dismissRunnable, 1500)

        return START_NOT_STICKY
    }

    private fun showPopup(sender: String, body: String) {
        if (windowManager == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }

        // Remove existing popup if any
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }

        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Distance from top
        }

        popupView = LayoutInflater.from(this).inflate(R.layout.popup_message_preview, null)
        
        val textSender = popupView?.findViewById<TextView>(R.id.textSender)
        val textPreview = popupView?.findViewById<TextView>(R.id.textPreview)

        textSender?.text = sender
        textPreview?.text = getThreeWordPreview(body)

        try {
            windowManager?.addView(popupView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun getThreeWordPreview(body: String): String {
        if (body.isBlank()) return ""
        val words = body.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size <= 3) {
            body
        } else {
            words.take(3).joinToString(" ") + "..."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        handler.removeCallbacks(dismissRunnable)
    }
}
