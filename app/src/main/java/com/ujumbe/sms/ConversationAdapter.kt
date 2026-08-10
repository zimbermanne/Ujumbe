package com.ujumbe.sms

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val context: Context,
    private var list: List<ConversationItem>,
    private val blockRepository: BlockRepository,
    private val onItemClick: (ConversationItem) -> Unit,
    private val onItemLongClick: (ConversationItem) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    fun updateList(newList: List<ConversationItem>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val contactName = ContactsHelper.getContactName(context, item.address)

        holder.textAddress.text = contactName
        holder.textSnippet.text = item.snippet

        // Formatted Time
        holder.textTime.text = formatTime(item.date)

        // Initials and Avatar Background Color
        val initials = initialsFor(contactName)
        holder.textAvatar.text = initials
        holder.textAvatar.background.setTint(colorFor(contactName))

        // Skin item for custom theme pack
        ThemeUtils.skinItemView(context, holder.itemView)

        // Unread Badge
        if (item.unreadCount > 0) {
            holder.textUnreadCount.text = item.unreadCount.toString()
            holder.textUnreadCount.visibility = View.VISIBLE
        } else {
            holder.textUnreadCount.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textAvatar: TextView = view.findViewById(R.id.textAvatar)
        val textAddress: TextView = view.findViewById(R.id.textAddress)
        val textSnippet: TextView = view.findViewById(R.id.textSnippet)
        val textTime: TextView = view.findViewById(R.id.textTime)
        val textUnreadCount: TextView = view.findViewById(R.id.textUnreadCount)
    }

    private fun initialsFor(name: String): String {
        val cleaned = name.trim().replace("+", "")
        if (cleaned.isEmpty()) return "U"
        val parts = cleaned.split("\\s+".toRegex())
        if (parts.size >= 2) {
            val first = parts[0].firstOrNull() ?: ' '
            val second = parts[1].firstOrNull() ?: ' '
            if (first.isLetter() && second.isLetter()) {
                return "$first$second".uppercase()
            }
        }
        return cleaned.take(2).uppercase()
    }

    private fun colorFor(name: String): Int {
        val colors = arrayOf(
            R.color.avatar_1,
            R.color.avatar_2,
            R.color.avatar_3,
            R.color.avatar_4,
            R.color.avatar_5,
            R.color.avatar_6
        )
        val index = Math.abs(name.hashCode()) % colors.size
        return ContextCompat.getColor(context, colors[index])
    }

    private fun formatTime(timeMs: Long): String {
        return if (DateUtils.isToday(timeMs)) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
        } else {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timeMs))
        }
    }
}
