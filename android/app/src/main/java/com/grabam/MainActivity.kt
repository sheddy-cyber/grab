package com.grabam

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.grabam.quicksettings.DownloadTile
import com.google.android.material.textfield.TextInputEditText
import com.grabam.utils.UrlUtils
import com.grabam.service.DownloadService

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        val btnDownload = findViewById<android.widget.Button>(R.id.btnDownload)
        val btnPaste = findViewById<android.widget.Button>(R.id.btnPaste)
        val btnAddTile = findViewById<android.widget.Button>(R.id.btnAddTile)
        val btnSettings = findViewById<android.view.View>(R.id.btnSettings)

        btnDownload.setOnClickListener {
            startDownload()
        }

        btnPaste.setOnClickListener {
            pasteFromClipboard()
        }

        btnAddTile.setOnClickListener {
            requestAddTile()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Request notification permission for Android 13+
        requestNotificationPermission()

        // Nudge the Quick Settings tile to refresh its state
        refreshTileState()
    }

    private fun refreshTileState() {
        try {
            val componentName = ComponentName(applicationContext, DownloadTile::class.java)
            android.service.quicksettings.TileService.requestListeningState(applicationContext, componentName)
        } catch (e: Exception) {
            android.util.Log.e("GrabAm", "Failed to refresh tile", e)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipItem = clipboard.primaryClip?.getItemAt(0)
        val text = clipItem?.coerceToText(this)?.toString()
        
        if (text != null) {
            etUrl.setText(text)
        } else {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val statusBarManager = getSystemService(android.app.StatusBarManager::class.java)
                statusBarManager.requestAddTileService(
                    ComponentName(this, DownloadTile::class.java),
                    getString(R.string.quick_settings_tile_label),
                    Icon.createWithResource(this, R.drawable.ic_download),
                    { it.run() },
                    { resultCode ->
                        if (resultCode == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                            Toast.makeText(this, "Tile added!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Please add the tile manually from your Quick Settings menu", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Please add the tile manually from your Quick Settings edit menu", Toast.LENGTH_LONG).show()
        }
    }

    private fun startDownload() {
        val rawUrl = etUrl.text.toString().trim()
        val videoUrl = UrlUtils.extractSupportedUrl(rawUrl)
        
        if (videoUrl != null) {
            val serviceIntent = Intent(this, DownloadService::class.java).apply {
                action = DownloadService.ACTION_START_DOWNLOAD
                putExtra(DownloadService.EXTRA_URL, videoUrl.url)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettingsDialog() {
        val sharedPrefs = getSharedPreferences("grab_am_settings", MODE_PRIVATE)
        val currentUrl = sharedPrefs.getString("backend_url", DownloadService.DEFAULT_BACKEND_URL) ?: DownloadService.DEFAULT_BACKEND_URL
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Backend Settings")
        
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            setText(currentUrl)
            hint = "https://your-backend.onrender.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(48, 24, 48, 24)
            }
            input.layoutParams = params
            addView(input)
        }
        
        builder.setView(container)
        builder.setMessage("Enter the base URL of your deployed backend. (YouTube extractor requires a public/working instance).")
        
        builder.setPositiveButton("Save") { dialog, _ ->
            val newUrl = input.text.toString().trim()
            if (newUrl.isNotEmpty()) {
                sharedPrefs.edit().putString("backend_url", newUrl).apply()
                Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        
        builder.setNeutralButton("Reset to Default") { dialog, _ ->
            sharedPrefs.edit().putString("backend_url", DownloadService.DEFAULT_BACKEND_URL).apply()
            Toast.makeText(this, "Reset to local backend", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        builder.show()
    }
}
