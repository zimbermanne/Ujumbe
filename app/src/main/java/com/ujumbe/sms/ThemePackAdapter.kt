package com.ujumbe.sms

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** A single row shown in the theme selector: either a built-in theme or an installed pack. */
data class ThemeListEntry(
    val id: String,
    val name: String,
    val author: String?,
    val swatchColor: Int,
    val isCustom: Boolean,
    val isActive: Boolean,
    val isPremium: Boolean = false,
    val price: String = "$0.00",
    val isPurchased: Boolean = true
)

class ThemePackAdapter(
    private var entries: List<ThemeListEntry>,
    private val onApply: (ThemeListEntry) -> Unit,
    private val onDelete: (ThemeListEntry) -> Unit,
    private val onPurchase: (ThemeListEntry) -> Unit
) : RecyclerView.Adapter<ThemePackAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val swatch: View = view.findViewById(R.id.viewSwatch)
        val name: TextView = view.findViewById(R.id.textThemeName)
        val subtitle: TextView = view.findViewById(R.id.textThemeSubtitle)
        val checkIcon: ImageView = view.findViewById(R.id.iconActiveCheck)
        val deleteButton: ImageView = view.findViewById(R.id.iconDeletePack)
        val priceText: TextView = view.findViewById(R.id.textPrice)
        val lockIcon: ImageView = view.findViewById(R.id.iconLock)
    }

    fun updateList(newEntries: List<ThemeListEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_pack, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.swatch.setBackgroundColor(entry.swatchColor)
        holder.name.text = entry.name
        holder.subtitle.text = if (entry.isCustom) {
            "Installed" + (entry.author?.let { " · by $it" } ?: "")
        } else {
            "Built-in"
        }

        if (entry.isPremium && !entry.isPurchased) {
            holder.priceText.visibility = View.VISIBLE
            holder.priceText.text = entry.price
            holder.lockIcon.visibility = View.VISIBLE
            holder.checkIcon.visibility = View.GONE
            holder.deleteButton.visibility = View.GONE
        } else {
            holder.priceText.visibility = View.GONE
            holder.lockIcon.visibility = View.GONE
            holder.checkIcon.visibility = if (entry.isActive) View.VISIBLE else View.INVISIBLE
            holder.deleteButton.visibility = if (entry.isCustom) View.VISIBLE else View.GONE
        }

        holder.itemView.setOnClickListener {
            if (entry.isPremium && !entry.isPurchased) {
                onPurchase(entry)
            } else {
                onApply(entry)
            }
        }
        holder.deleteButton.setOnClickListener { onDelete(entry) }
    }

    override fun getItemCount(): Int = entries.size
}
