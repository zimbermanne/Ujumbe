package com.ujumbe.sms

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BlockListAdapter(
    private val context: Context,
    private var list: List<ConversationItem>,
    private val blockRepository: BlockRepository,
    private val onBlockChanged: (ConversationItem, Boolean) -> Unit
) : RecyclerView.Adapter<BlockListAdapter.ViewHolder>() {

    fun updateList(newList: List<ConversationItem>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_block_toggle, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val contactName = ContactsHelper.getContactName(context, item.address)

        holder.textName.text = contactName

        // Initials and Avatar
        val initials = initialsFor(contactName)
        holder.textAvatar.text = initials
        holder.textAvatar.background.setTint(colorFor(contactName))

        // Block status
        val isBlocked = blockRepository.isBlocked(item.address)
        holder.switchBlocked.setOnCheckedChangeListener(null)
        holder.switchBlocked.isChecked = isBlocked
        holder.textStatus.visibility = if (isBlocked) View.VISIBLE else View.GONE

        holder.switchBlocked.setOnCheckedChangeListener { _, isChecked ->
            blockRepository.setBlocked(item.address, isChecked)
            holder.textStatus.visibility = if (isChecked) View.VISIBLE else View.GONE
            onBlockChanged(item, isChecked)
        }
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textAvatar: TextView = view.findViewById(R.id.textAvatar)
        val textName: TextView = view.findViewById(R.id.textName)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val switchBlocked: SwitchCompat = view.findViewById(R.id.switchBlocked)
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
}
