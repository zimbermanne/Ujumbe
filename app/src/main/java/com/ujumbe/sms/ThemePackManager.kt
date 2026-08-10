package com.ujumbe.sms

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Persists user-installed [ThemePack]s and tracks which one (if any) is active.
 *
 * Packs are stored as a JSON array in SharedPreferences. This keeps the
 * "install a theme" flow file-free and offline: a user can install a theme by
 * picking a .json file with a share sheet, or a future update could fetch one
 * from a URL using the same installFromJson() entry point.
 */
object ThemePackManager {
    private const val PREFS_NAME = "theme_packs_prefs"
    private const val KEY_PACKS = "installed_packs"

    fun getInstalledPacks(context: Context): List<ThemePack> {
        val raw = prefs(context).getString(KEY_PACKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                try {
                    parsePack(array.getJSONObject(i))
                } catch (e: Exception) {
                    null // skip a corrupted entry instead of failing the whole list
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getPack(context: Context, id: String): ThemePack? {
        return getInstalledPacks(context).firstOrNull { it.id == id }
    }

    /**
     * Installs (or updates, if the id already exists) a theme pack from raw JSON text.
     * Returns the parsed pack on success. Throws with a user-facing message on failure.
     */
    fun installFromJson(context: Context, rawJson: String): ThemePack {
        val pack = ThemePack.fromJsonString(rawJson)
        val existing = getInstalledPacks(context).filterNot { it.id == pack.id }
        val updated = existing + pack
        savePacks(context, updated)
        return pack
    }

    /** Reads the given content Uri (e.g. from a file picker) and installs it. */
    fun installFromUri(context: Context, uri: Uri): ThemePack {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IllegalArgumentException("Could not read the selected file")
        return installFromJson(context, text)
    }

    private const val MAX_DOWNLOAD_BYTES = 200 * 1024 // theme JSON should be tiny; guard against abuse
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /**
     * Downloads a theme pack's JSON from a remote URL and installs it.
     * Runs the network call off the main thread. Only http/https URLs are
     * accepted, and the response body is size-capped so a malicious or
     * misbehaving server can't be used to exhaust memory.
     */
    suspend fun installFromUrl(context: Context, urlString: String): ThemePack {
        val trimmed = urlString.trim()
        require(trimmed.isNotEmpty()) { "Enter a theme URL" }

        val json = withContext(Dispatchers.IO) {
            val url = try {
                URL(trimmed)
            } catch (e: Exception) {
                throw IllegalArgumentException("That doesn't look like a valid URL")
            }
            if (url.protocol != "http" && url.protocol != "https") {
                throw IllegalArgumentException("Theme URL must start with http:// or https://")
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalArgumentException("Server returned an error (HTTP $code)")
                }

                val bytes = connection.inputStream.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        total += read
                        if (total > MAX_DOWNLOAD_BYTES) {
                            throw IllegalArgumentException("That theme file is too large")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                }
                bytes.toString(Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException("Couldn't reach that URL: ${e.message ?: "network error"}")
            } finally {
                connection?.disconnect()
            }
        }

        return installFromJson(context, json)
    }

    fun deletePack(context: Context, id: String) {
        val remaining = getInstalledPacks(context).filterNot { it.id == id }
        savePacks(context, remaining)

        // If the deleted pack was active, fall back to the default built-in theme.
        val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        if (themePrefs.getString("active_pack_id", null) == id) {
            themePrefs.edit {
                putString("theme_color", "blue")
                remove("active_pack_id")
            }
        }
    }

    /** Marks [id] as the active theme; ThemeUtils reads this on the next applyTheme() call. */
    fun setActivePack(context: Context, id: String) {
        val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        themePrefs.edit {
            putString("theme_color", "custom")
            putString("active_pack_id", id)
        }
    }

    fun getActivePackId(context: Context): String? {
        val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        if (themePrefs.getString("theme_color", "blue") != "custom") return null
        return themePrefs.getString("active_pack_id", null)
    }

    /**
     * Looks through assets/themes and installs any that aren't already there.
     * This makes our "Marketplace" themes show up automatically.
     */
    fun preinstallDefaultThemes(context: Context) {
        try {
            val themeFiles = context.assets.list("themes") ?: return
            val installedIds = getInstalledPacks(context).map { it.id }.toSet()

            themeFiles.filter { it.endsWith(".json") }.forEach { fileName ->
                val json = context.assets.open("themes/$fileName").use { it.bufferedReader().readText() }
                val pack = ThemePack.fromJsonString(json)
                if (pack.id !in installedIds) {
                    installFromJson(context, json)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parsePack(json: org.json.JSONObject): ThemePack = ThemePack.fromJsonString(json.toString())

    private fun savePacks(context: Context, packs: List<ThemePack>) {
        val array = JSONArray()
        packs.forEach { array.put(it.toJson()) }
        prefs(context).edit { putString(KEY_PACKS, array.toString()) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
