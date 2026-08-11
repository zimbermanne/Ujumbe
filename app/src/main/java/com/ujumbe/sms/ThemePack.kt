package com.ujumbe.sms

import org.json.JSONObject

/**
 * A single light or dark color variant within a theme pack.
 */
data class ThemeVariant(
    val primary: String,
    val primaryDark: String,
    val accent: String,
    val background: String,
    val text: String,
    val primaryGradientEnd: String? = null,
    val backgroundAlpha: Float = 1.0f,
    val surfaceAlpha: Float = 1.0f,
    val surfaceColor: String? = null,
    val iconColor: String? = null,
    val backgroundImageUrl: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("primary", primary)
        put("primaryDark", primaryDark)
        put("accent", accent)
        put("background", background)
        put("text", text)
        if (primaryGradientEnd != null) put("primaryGradientEnd", primaryGradientEnd)
        put("backgroundAlpha", backgroundAlpha.toDouble())
        put("surfaceAlpha", surfaceAlpha.toDouble())
        if (surfaceColor != null) put("surfaceColor", surfaceColor)
        if (iconColor != null) put("iconColor", iconColor)
        if (backgroundImageUrl != null) put("backgroundImageUrl", backgroundImageUrl)
    }

    companion object {
        fun fromJson(json: JSONObject): ThemeVariant {
            return ThemeVariant(
                primary = json.getString("primary"),
                primaryDark = json.getString("primaryDark"),
                accent = json.getString("accent"),
                background = json.getString("background"),
                text = json.getString("text"),
                primaryGradientEnd = json.optString("primaryGradientEnd", "").trim().ifEmpty { null },
                backgroundAlpha = json.optDouble("backgroundAlpha", 1.0).toFloat(),
                surfaceAlpha = json.optDouble("surfaceAlpha", 1.0).toFloat(),
                surfaceColor = json.optString("surfaceColor", "").trim().ifEmpty { null },
                iconColor = json.optString("iconColor", "").trim().ifEmpty { null },
                backgroundImageUrl = json.optString("backgroundImageUrl", "").trim().ifEmpty { null }
            )
        }
    }
}

/**
 * A user-installable theme pack. A pack supplies both a light and a dark
 * variant so it still respects the existing app-wide dark mode switch, plus
 * typography/shape/wallpaper choices so an installed theme changes the whole
 * feel of the app, not just its accent colors.
 */
data class ThemePack(
    val id: String,
    val name: String,
    val author: String,
    val light: ThemeVariant,
    val dark: ThemeVariant,
    val font: String = "sans-serif",
    val bubbleStyle: String = "rounded",
    val wallpaper: String? = null,
    val animationType: String = "standard",
    val layoutType: String = "default",
    val isPremium: Boolean = false,
    val price: String = "$0.00",
    val customDrawables: Map<String, String> = emptyMap() // key: name, value: url
) {
    fun variantFor(mode: String): ThemeVariant = if (mode == "dark") dark else light

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("author", author)
        put("colors", JSONObject().apply {
            put("light", light.toJson())
            put("dark", dark.toJson())
        })
        put("font", font)
        put("bubbleStyle", bubbleStyle)
        if (wallpaper != null) put("wallpaper", wallpaper)
        put("animationType", animationType)
        put("layoutType", layoutType)
        put("isPremium", isPremium)
        put("price", price)
        if (customDrawables.isNotEmpty()) {
            val drawablesJson = JSONObject()
            customDrawables.forEach { (k, v) -> drawablesJson.put(k, v) }
            put("customDrawables", drawablesJson)
        }
    }

    companion object {
        val VALID_FONTS = setOf("sans-serif", "serif", "monospace", "cursive", "casual")
        val VALID_BUBBLE_STYLES = setOf("rounded", "sharp", "pill")
        val VALID_ANIMATIONS = setOf("standard", "fade", "slide", "bounce")
        val VALID_LAYOUTS = setOf("default", "compact", "loose", "floating")

        fun fromJsonString(raw: String): ThemePack {
            val json = JSONObject(raw)

            val id = json.optString("id").trim()
            val name = json.optString("name").trim()
            require(id.isNotEmpty()) { "Theme is missing an \"id\" field" }
            require(name.isNotEmpty()) { "Theme is missing a \"name\" field" }
            require(id.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                "Theme \"id\" may only contain letters, numbers, - and _"
            }

            val author = json.optString("author", "Unknown")
            val colors = json.optJSONObject("colors")
                ?: throw IllegalArgumentException("Theme is missing a \"colors\" object")

            val lightJson = colors.optJSONObject("light")
                ?: throw IllegalArgumentException("Theme is missing \"colors.light\"")
            val darkJson = colors.optJSONObject("dark")
                ?: throw IllegalArgumentException("Theme is missing \"colors.dark\"")

            val light = try {
                ThemeVariant.fromJson(lightJson)
            } catch (e: Exception) {
                throw IllegalArgumentException("colors.light is missing a required color field")
            }
            val dark = try {
                ThemeVariant.fromJson(darkJson)
            } catch (e: Exception) {
                throw IllegalArgumentException("colors.dark is missing a required color field")
            }

            val font = json.optString("font", "sans-serif").trim().ifEmpty { "sans-serif" }
            require(font in VALID_FONTS) {
                "\"font\" must be one of: ${VALID_FONTS.joinToString(", ")}"
            }

            val bubbleStyle = json.optString("bubbleStyle", "rounded").trim().ifEmpty { "rounded" }
            require(bubbleStyle in VALID_BUBBLE_STYLES) {
                "\"bubbleStyle\" must be one of: ${VALID_BUBBLE_STYLES.joinToString(", ")}"
            }

            val animationType = json.optString("animationType", "standard").trim().ifEmpty { "standard" }
            require(animationType in VALID_ANIMATIONS) {
                "\"animationType\" must be one of: ${VALID_ANIMATIONS.joinToString(", ")}"
            }

            val layoutType = json.optString("layoutType", "default").trim().ifEmpty { "default" }
            require(layoutType in VALID_LAYOUTS) {
                "\"layoutType\" must be one of: ${VALID_LAYOUTS.joinToString(", ")}"
            }

            val isPremium = json.optBoolean("isPremium", false)
            val price = json.optString("price", "$0.00")
            val wallpaper = json.optString("wallpaper", "").trim().ifEmpty { null }

            val customDrawables = mutableMapOf<String, String>()
            json.optJSONObject("customDrawables")?.let { drawablesJson ->
                val keys = drawablesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    customDrawables[key] = drawablesJson.getString(key)
                }
            }

            return ThemePack(
                id = id, name = name, author = author, light = light, dark = dark,
                font = font, bubbleStyle = bubbleStyle, wallpaper = wallpaper,
                animationType = animationType, layoutType = layoutType,
                isPremium = isPremium, price = price,
                customDrawables = customDrawables
            )
        }
    }
}
