package com.ujumbe.sms

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.view.isNotEmpty
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ujumbe.sms.ThemeUtils.applyCustomPackSkin

object ThemeUtils {

    fun applyTheme(activity: Activity) {
        val prefs = activity.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "light") ?: "light"

        // Set AppCompat night mode
        val nightMode = if (mode == "dark") {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }

        val packId = prefs.getString("active_pack_id", null)
        val pack = packId?.let { ThemePackManager.getPack(activity, it) }
        
        if (pack != null) {
            applyCustomPackTheme(activity, pack, mode)
        } else {
            // Neutral base theme
            activity.setTheme(R.style.Theme_Ujumbe)
            val bgColor = if (mode == "dark") Color.BLACK else Color.WHITE
            activity.window.setBackgroundDrawable(bgColor.toDrawable())
            val contentLayout = activity.findViewById<ViewGroup>(android.R.id.content)
            val rootChild = if (contentLayout != null && contentLayout.isNotEmpty()) contentLayout.getChildAt(0) else null
            rootChild?.setBackgroundColor(bgColor)
        }
    }

    fun applyFont(context: Context, rootView: View) {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val packId = prefs.getString("active_pack_id", null)
        val activePack = packId?.let { ThemePackManager.getPack(context, it) }
        
        val font = activePack?.font
            ?: prefs.getString("theme_font", "sans-serif")
            ?: "sans-serif"
        val typeface = when (font) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            "casual" -> Typeface.create("casual", Typeface.NORMAL)
            else -> Typeface.SANS_SERIF
        }
        applyTypefaceToViewHierarchy(rootView, typeface)
    }

    private fun applyTypefaceToViewHierarchy(view: View, typeface: Typeface) {
        if (view is TextView) {
            view.typeface = typeface
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTypefaceToViewHierarchy(view.getChildAt(i), typeface)
            }
        }
    }

    /**
     * Applies an installed [ThemePack] to [activity]. Android theme attributes
     * (colorPrimary, colorAccent, etc.) are resolved at compile time from
     * styles.xml, so an arbitrary user-supplied color can't be injected into
     * them at runtime. Instead this sets a neutral Material base theme, then
     * paints the pack's colors directly onto the window and, once the layout
     * exists, onto the common widgets (buttons, switches, icons) via
     * [applyCustomPackSkin]. This is what lets an installed theme restyle the
     * whole app without shipping compiled resources for it.
     */
    @Suppress("DEPRECATION")
    private fun applyCustomPackTheme(activity: Activity, pack: ThemePack, mode: String) {
        val baseTheme = R.style.Theme_Ujumbe
        activity.setTheme(baseTheme)

        val variant = pack.variantFor(mode)
        val backgroundColor = safeParseColor(variant.background, if (mode == "dark") Color.BLACK else Color.WHITE)
        activity.window.setBackgroundDrawable(backgroundColor.toDrawable())

        activity.window.statusBarColor = safeParseColor(variant.primaryDark, backgroundColor)
        val decorView = activity.window.decorView
        val isLightBg = isColorLight(backgroundColor)
        var flags = decorView.systemUiVisibility
        flags = if (isLightBg) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        decorView.systemUiVisibility = flags
    }

    /**
     * Walks the current activity's view tree and re-skins the common widget
     * types with the active theme pack's colors. Call this after
     * setContentView() (e.g. from onPostCreate), mirroring how applyFont()
     * is already applied post-layout.
     */
    fun applyCustomPackSkin(activity: Activity) {
        val prefs = activity.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val packId = prefs.getString("active_pack_id", null) ?: return
        val pack = ThemePackManager.getPack(activity, packId) ?: return
        val mode = prefs.getString("theme_mode", "light") ?: "light"
        val variant = pack.variantFor(mode)

        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val backgroundColor = safeParseColor(variant.background, Color.WHITE)
        val primaryColor = safeParseColor(variant.primary, Color.DKGRAY)
        val accentColor = safeParseColor(variant.accent, primaryColor)
        val textColor = safeParseColor(variant.text, Color.BLACK)
        val surfaceColor = variant.surfaceColor?.let { safeParseColor(it, primaryColor) } ?: primaryColor
        val iconColor = variant.iconColor?.let { safeParseColor(it, accentColor) } ?: accentColor

        val gradientEndColor = variant.primaryGradientEnd?.let { safeParseColor(it, primaryColor) }

        if (root.isNotEmpty()) {
            // Check for background image first
            val backgroundImageFile = ThemePackManager.getThemeAssetPath(activity, packId, "bg_main.jpg")
            if (backgroundImageFile.exists()) {
                val drawable = android.graphics.drawable.Drawable.createFromPath(backgroundImageFile.absolutePath)
                root.getChildAt(0).background = drawable
            } else {
                val wallpaperColor = pack.wallpaper?.let { safeParseColor(it, backgroundColor) } ?: backgroundColor
                root.getChildAt(0).setBackgroundColor(wallpaperColor)
            }
        }
        skinViewHierarchy(root, primaryColor, accentColor, textColor, gradientEndColor, surfaceColor, iconColor)

        // Color the chat/inbox header bar like the reference template (a
        // filled primary-color bar with contrasting icon/text), if this
        // screen has one. A theme can make this a diagonal gradient instead
        // of a flat fill by setting primaryGradientEnd.
        val headerContrast = if (isColorLight(primaryColor)) Color.BLACK else Color.WHITE
        listOf(R.id.headerThread, R.id.headerNormal).forEach { headerId ->
            activity.findViewById<View>(headerId)?.let { header ->
                if (gradientEndColor != null) {
                    header.background = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        intArrayOf(primaryColor, gradientEndColor)
                    )
                } else {
                    header.setBackgroundColor(primaryColor)
                }
                tintHeaderContents(header, headerContrast)
            }
        }
    }

    private fun tintHeaderContents(view: View, contrastColor: Int) {
        when (view) {
            is TextView -> view.setTextColor(contrastColor)
            is ImageView -> view.setColorFilter(contrastColor)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                tintHeaderContents(view.getChildAt(i), contrastColor)
            }
        }
    }

    /**
     * Re-skins a specific view (e.g. a list item) using the active theme pack.
     */
    fun skinItemView(context: Context, itemView: View) {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val packId = prefs.getString("active_pack_id", null) ?: return
        val pack = ThemePackManager.getPack(context, packId) ?: return
        val mode = prefs.getString("theme_mode", "light") ?: "light"
        val variant = pack.variantFor(mode)

        val primaryColor = safeParseColor(variant.primary, Color.DKGRAY)
        val accentColor = safeParseColor(variant.accent, primaryColor)
        val textColor = safeParseColor(variant.text, Color.BLACK)
        val surfaceColor = variant.surfaceColor?.let { safeParseColor(it, primaryColor) } ?: primaryColor
        val iconColor = variant.iconColor?.let { safeParseColor(it, accentColor) } ?: accentColor
        val gradientEndColor = variant.primaryGradientEnd?.let { safeParseColor(it, primaryColor) }

        skinViewHierarchy(itemView, primaryColor, accentColor, textColor, gradientEndColor, surfaceColor, iconColor)
    }

    private fun skinViewHierarchy(
        view: View,
        primaryColor: Int,
        accentColor: Int,
        textColor: Int,
        gradientEndColor: Int?,
        surfaceColor: Int,
        iconColor: Int
    ) {
        when (view) {
            is FloatingActionButton -> {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
            }
            is MaterialButton -> {
                if (gradientEndColor != null) {
                    // Tint lists can't express a gradient, so give gradient
                    // themes a real GradientDrawable background instead,
                    // matching pill-shaped gradient CTAs like "Sign up".
                    view.backgroundTintList = null
                    val corner = view.height.takeIf { it > 0 }?.let { it / 2f }
                        ?: (24 * view.resources.displayMetrics.density)
                    view.background = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        intArrayOf(primaryColor, gradientEndColor)
                    ).apply { cornerRadius = corner }
                } else {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                }
            }
            is SwitchCompat -> {
                view.thumbTintList = android.content.res.ColorStateList.valueOf(accentColor)
            }
            is Button -> {
                view.setTextColor(accentColor)
            }
            is TextView -> {
                if (view.tag == "theme_text_primary") {
                    view.setTextColor(textColor)
                } else if (view.tag == "theme_text_secondary") {
                    view.setTextColor(textColor)
                    view.alpha = 0.7f
                }
            }
        }
        
        // Recolor generic chrome via tags
        when (view.tag) {
            "theme_tint" -> if (view is ImageView) view.setColorFilter(iconColor)
            "theme_pill" -> {
                val drawable = view.background
                if (drawable is android.graphics.drawable.GradientDrawable) {
                    drawable.setColor(accentColor)
                    drawable.alpha = 40 // semi-transparent pill
                }
            }
            "theme_panel" -> {
                val drawable = view.background
                if (drawable is android.graphics.drawable.GradientDrawable) {
                    drawable.setColor(surfaceColor)
                    drawable.alpha = 25
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                skinViewHierarchy(view.getChildAt(i), primaryColor, accentColor, textColor, gradientEndColor, surfaceColor, iconColor)
            }
        }
    }

    private fun safeParseColor(hex: String, fallback: Int): Int {
        return try {
            hex.toColorInt()
        } catch (_: Exception) {
            fallback
        }
    }

    private fun isColorLight(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness < 0.5
    }

    /** Colors + shape for chat bubbles when a custom theme pack is active, or null to keep the built-in bubble drawables. */
    data class BubbleColors(
        val sentBg: Int,
        val sentText: Int,
        val sentBgGradientEnd: Int?, // non-null to render sent bubbles as a diagonal gradient
        val receivedBg: Int,
        val receivedText: Int,
        val bubbleStyle: String // "rounded" | "sharp" | "pill"
    )

    fun getActiveBubbleColors(context: Context): BubbleColors? {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val packId = prefs.getString("active_pack_id", null) ?: return null
        val pack = ThemePackManager.getPack(context, packId) ?: return null
        val mode = prefs.getString("theme_mode", "light") ?: "light"
        val variant = pack.variantFor(mode)

        val primary = safeParseColor(variant.primary, Color.DKGRAY)
        val text = safeParseColor(variant.text, Color.BLACK)
        val bg = safeParseColor(variant.background, Color.WHITE)
        val gradientEnd = variant.primaryGradientEnd?.let { safeParseColor(it, primary) }

        // Sent bubbles use the pack's primary color (optionally as a
        // gradient) so they pop the way they do in the reference design;
        // received bubbles stay a plain surface close to the background.
        val receivedSurface = if (isColorLight(bg)) Color.WHITE else Color.argb(255, 40, 40, 40)
        val sentTextColor = if (isColorLight(primary)) Color.BLACK else Color.WHITE

        return BubbleColors(
            sentBg = primary,
            sentText = sentTextColor,
            sentBgGradientEnd = gradientEnd,
            receivedBg = receivedSurface,
            receivedText = text,
            bubbleStyle = pack.bubbleStyle
        )
    }
}
