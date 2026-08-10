package com.ujumbe.sms

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val context: Context,
    private var list: List<SmsMessageItem>,
    private val onItemLongClick: (SmsMessageItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 0
        private const val TYPE_RECEIVED = 1
    }

    fun updateList(newList: List<SmsMessageItem>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (list[position].isSent) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SENT) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_message_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.item_message_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    private var lastAnimatedMessageId: String? = null

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = list[position]
        val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.date))
        // Installed theme packs don't ship compiled bubble drawables, so if one
        // is active we retint the existing bubble drawable at bind time instead
        // of leaving every conversation on the fixed "glass" bubble colors.
        val bubbleColors = ThemeUtils.getActiveBubbleColors(context)

        if (holder is SentViewHolder) {
            holder.textBody.text = item.body
            holder.textTime.text = formattedTime
            tintBubble(holder.textBody, bubbleColors?.sentBg, bubbleColors?.sentText, bubbleColors?.sentBgGradientEnd, bubbleColors?.bubbleStyle, isSent = true)
            holder.itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        } else if (holder is ReceivedViewHolder) {
            val isLatestMessage = position == list.size - 1
            val isRecent = System.currentTimeMillis() - item.date < 10000
            val notYetAnimated = item.id != lastAnimatedMessageId

            if (isLatestMessage && isRecent && notYetAnimated) {
                lastAnimatedMessageId = item.id
                holder.textBody.animateText(item.body)
            } else {
                holder.textBody.text = item.body
            }
            holder.textTime.text = formattedTime
            tintBubble(holder.textBody, bubbleColors?.receivedBg, bubbleColors?.receivedText, null, bubbleColors?.bubbleStyle, isSent = false)
            holder.itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }

    private fun tintBubble(
        textView: TextView,
        bgColor: Int?,
        textColor: Int?,
        bgGradientEnd: Int?,
        bubbleStyle: String?,
        isSent: Boolean
    ) {
        if (bgColor == null || textColor == null) return // no custom pack active - keep XML defaults
        // Build the bubble shape fresh so an installed theme can change the
        // silhouette (sharp corners, pill, etc.), not just retint the app's
        // fixed "rounded with one flat tail corner" bubble shape.
        val radiusDp = when (bubbleStyle) {
            "sharp" -> 4f
            "pill" -> 28f
            else -> 18f // "rounded" (default)
        }
        val tailRadiusDp = 4f
        val density = textView.resources.displayMetrics.density
        val radiusPx = radiusDp * density
        val tailPx = tailRadiusDp * density

        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            if (bgGradientEnd != null) {
                orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(bgColor, bgGradientEnd)
            } else {
                setColor(bgColor)
            }
            // Flatten one bottom corner to keep the familiar "chat tail" cue,
            // matching which side sent/received bubbles hug.
            cornerRadii = if (isSent) {
                floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, tailPx, tailPx, radiusPx, radiusPx)
            } else {
                floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, tailPx, tailPx)
            }
        }
        textView.background = drawable
        textView.setTextColor(textColor)
    }

    override fun getItemCount(): Int = list.size

    class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textBody: TextView = view.findViewById(R.id.textBody)
        val textTime: TextView = view.findViewById(R.id.textTime)
    }

    class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textBody: TypewriterTextView = view.findViewById(R.id.textBody)
        val textTime: TextView = view.findViewById(R.id.textTime)
    }
}
