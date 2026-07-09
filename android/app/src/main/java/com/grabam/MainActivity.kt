package com.grabam

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
        val btnSettings = findViewById<android.view.View>(R.id.btnSettings)

        btnDownload.setOnClickListener {
            startDownload()
        }

        btnPaste.setOnClickListener {
            pasteFromClipboard()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Request notification permission for Android 13+
        requestNotificationPermission()

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

    private fun startDownload() {
        val rawUrl = etUrl.text.toString().trim()
        val videoUrl = UrlUtils.extractSupportedUrl(rawUrl)
        
        if (videoUrl != null) {
            var finished = false
            val finishAction = Runnable {
                if (!finished) {
                    finished = true
                    DownloadService.onServiceStarted = null
                    finish()
                }
            }

            // Wait for service to register as foreground before finishing the activity
            DownloadService.onServiceStarted = {
                runOnUiThread(finishAction)
            }

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
            
            // Post a 2-second timeout safety net to ensure we finish the activity even if service start fails
            window.decorView.postDelayed(finishAction, 2000)
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
            hint = "https://sheddycyber-grab-am.hf.space"
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
        builder.setMessage("Enter the base URL of a custom backend if self-hosting. Most users don't need to change this.")
        
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
            Toast.makeText(this, "Reset to default backend", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        builder.show()
    }
}
