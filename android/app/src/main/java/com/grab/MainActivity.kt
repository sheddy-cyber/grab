package com.grab

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
import com.grab.utils.UrlUtils
import com.grab.utils.NetworkUtils
import com.grab.service.DownloadService
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import android.net.Uri

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
        
        // Request storage permission for Android 9 and below
        requestStoragePermission()

        setupCreatedByText()
    }

    private fun setupCreatedByText() {
        val tvCreatedBy = findViewById<TextView>(R.id.tvCreatedBy)
        val createdByText = "Created by Kris Shedrach"
        val spannableString = SpannableString(createdByText)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://krisshedrach.dev"))
                startActivity(intent)
            }
        }
        spannableString.setSpan(clickableSpan, 11, 24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvCreatedBy.text = spannableString
        tvCreatedBy.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != 
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 102)
            }
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

    private fun startDownload() {
        val rawUrl = etUrl.text.toString().trim()
        val videoUrl = UrlUtils.extractSupportedUrl(rawUrl)
        
        if (videoUrl != null) {
            if (!NetworkUtils.isInternetAvailable(this)) {
                Toast.makeText(this, R.string.no_internet, Toast.LENGTH_SHORT).show()
                return
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
            
            // Clear the URL field after starting download
            etUrl.text?.clear()
        } else {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettingsDialog() {
        val sharedPrefs = getSharedPreferences("grab_am_settings", MODE_PRIVATE)
        val currentUrl = sharedPrefs.getString("backend_url", DownloadService.DEFAULT_BACKEND_URL) ?: DownloadService.DEFAULT_BACKEND_URL
        
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        builder.setTitle("Backend Settings")
        
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            setText(currentUrl)
            hint = "https://grab-am.onrender.com"
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
