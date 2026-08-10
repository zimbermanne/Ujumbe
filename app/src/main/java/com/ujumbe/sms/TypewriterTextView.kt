package com.ujumbe.sms

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class TypewriterTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var textSequence: CharSequence? = null
    private var index = 0
    private var delayMs: Long = 20

    private val handler = Handler(Looper.getMainLooper())
    private val runnable: Runnable = object : Runnable {
        override fun run() {
            text = textSequence?.subSequence(0, index++)
            if (index <= (textSequence?.length ?: 0)) {
                handler.postDelayed(this, delayMs)
            }
        }
    }

    fun animateText(textToAnimate: CharSequence) {
        textSequence = textToAnimate
        index = 0
        text = ""
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, delayMs)
    }

    fun setCharacterDelay(delay: Long) {
        delayMs = delay
    }
}
