package com.clippd

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.clippd.service.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        val btnDownload = findViewById<android.widget.Button>(R.id.btnDownload)
        val btnPaste = findViewById<android.widget.Button>(R.id.btnPaste)

        btnDownload.setOnClickListener {
            startDownload()
        }

        btnPaste.setOnClickListener {
            pasteFromClipboard()
        }

        // Handle shared URL
        handleSharedIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                val url = extractUrl(sharedText)
                if (url != null) {
                    etUrl.setText(url)
                    startDownload()
                } else {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipItem = clipboard.primaryClip?.getItemAt(0)
        val text = clipItem?.text?.toString()
        
        if (text != null) {
            etUrl.setText(text)
        } else {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownload() {
        val url = etUrl.text.toString().trim()
        
        if (isValidTwitterUrl(url)) {
            val serviceIntent = Intent(this, DownloadService::class.java).apply {
                action = DownloadService.ACTION_START_DOWNLOAD
                putExtra(DownloadService.EXTRA_URL, url)
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

    private fun extractUrl(text: String): String? {
        val uri = Uri.parse(text)
        if (uri.scheme != null && (uri.scheme == "http" || uri.scheme == "https")) {
            return text
        }
        
        // Try to extract URL from text
        val urlPattern = """(https?://[^\s]+)""".toRegex()
        val match = urlPattern.find(text)
        return match?.value
    }

    private fun isValidTwitterUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            
            // Support twitter.com and x.com
            host == "twitter.com" || 
            host == "www.twitter.com" || 
            host == "x.com" || 
            host == "www.x.com" ||
            host.endsWith(".twitter.com") ||
            host.endsWith(".x.com")
        } catch (e: Exception) {
            false
        }
    }
}
