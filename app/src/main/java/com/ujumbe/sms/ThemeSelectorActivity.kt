package com.ujumbe.sms

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Lets the user browse the built-in accent themes and any theme packs
 * they've installed, apply one, or install a new one from a JSON file.
 */
class ThemeSelectorActivity : BaseActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ThemePackAdapter

    private val installThemeLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) installTheme(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_selector)

        // Ensure assets/themes are visible in the list
        ThemePackManager.preinstallDefaultThemes(this)

        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }

        recycler = findViewById(R.id.recyclerThemes)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ThemePackAdapter(
            entries = emptyList(),
            onApply = { entry -> applyEntry(entry) },
            onDelete = { entry -> confirmDelete(entry) },
            onPurchase = { entry -> initiatePurchase(entry) }
        )
        recycler.adapter = adapter

        findViewById<MaterialButton>(R.id.buttonInstallTheme).setOnClickListener {
            showInstallSourceDialog()
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val activePackId = prefs.getString("active_pack_id", null)

        val builtIns = listOf(
            ThemeListEntry("default", "Default", null, Color.parseColor("#1E52C4"), false, activePackId == null),
        )

        val custom = ThemePackManager.getInstalledPacks(this).map { pack ->
            ThemeListEntry(
                id = pack.id,
                name = pack.name,
                author = pack.author,
                swatchColor = try {
                    Color.parseColor(pack.light.primary)
                } catch (e: Exception) {
                    Color.GRAY
                },
                isCustom = true,
                isActive = activePackId == pack.id,
                isPremium = pack.isPremium,
                price = pack.price,
                isPurchased = BillingManager.isThemePurchased(this, pack.id)
            )
        }

        adapter.updateList(builtIns + custom)
    }

    private fun initiatePurchase(entry: ThemeListEntry) {
        AlertDialog.Builder(this)
            .setTitle("Purchase ${entry.name}?")
            .setMessage("This is a premium theme. Would you like to buy it for ${entry.price}?")
            .setPositiveButton("Buy Now") { _, _ ->
                val progress = AlertDialog.Builder(this)
                    .setTitle("Processing...")
                    .setMessage("Communicating with Google Play")
                    .setCancelable(false)
                    .show()

                BillingManager.purchaseTheme(this, entry.id) { success ->
                    progress.dismiss()
                    if (success) {
                        Toast.makeText(this, "Purchase successful! Enjoy your theme.", Toast.LENGTH_LONG).show()
                        refreshList()
                    } else {
                        Toast.makeText(this, "Purchase failed. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }


    private fun applyEntry(entry: ThemeListEntry) {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        if (entry.isCustom) {
            ThemePackManager.setActivePack(this, entry.id)
        } else {
            prefs.edit {
                remove("active_pack_id")
            }
        }
        Toast.makeText(this, "Applied \"${entry.name}\"", Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun confirmDelete(entry: ThemeListEntry) {
        AlertDialog.Builder(this)
            .setTitle("Remove \"${entry.name}\"?")
            .setMessage("This will uninstall the theme. This cannot be undone.")
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                ThemePackManager.deletePack(this, entry.id)
                Toast.makeText(this, "Theme removed", Toast.LENGTH_SHORT).show()
                refreshList()
                // If we just deleted the active theme, BaseActivity's onResume
                // check will notice active_pack_id changed and recreate us.
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showInstallSourceDialog() {
        val options = arrayOf(
            getString(R.string.install_theme_from_file),
            getString(R.string.install_theme_from_url)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.install_theme)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchFilePicker()
                    1 -> showUrlInstallDialog()
                }
            }
            .show()
    }

    private fun launchFilePicker() {
        try {
            installThemeLauncher.launch("*/*")
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUrlInstallDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.theme_url_hint)
            setSingleLine(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.install_theme_from_url)
            .setView(input)
            .setPositiveButton(R.string.download, null) // set below so we can control dismissal
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = input.text?.toString().orEmpty()
                installFromUrl(url, dialog)
            }
        }
        dialog.show()
    }

    private fun installFromUrl(url: String, dialog: AlertDialog) {
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = false
        positiveButton.text = getString(R.string.downloading)

        lifecycleScope.launch {
            try {
                val pack = ThemePackManager.installFromUrl(this@ThemeSelectorActivity, url)
                Toast.makeText(
                    this@ThemeSelectorActivity,
                    getString(R.string.theme_installed, pack.name),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
                refreshList()
            } catch (e: Exception) {
                positiveButton.isEnabled = true
                positiveButton.text = getString(R.string.download)
                AlertDialog.Builder(this@ThemeSelectorActivity)
                    .setTitle(R.string.theme_install_failed)
                    .setMessage(e.message ?: getString(R.string.theme_install_failed_generic))
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun installTheme(uri: Uri) {
        try {
            val pack = ThemePackManager.installFromUri(this, uri)
            Toast.makeText(this, "Installed \"${pack.name}\"", Toast.LENGTH_SHORT).show()
            refreshList()
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Couldn't install theme")
                .setMessage(e.message ?: "The selected file isn't a valid Ujumbe theme.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
