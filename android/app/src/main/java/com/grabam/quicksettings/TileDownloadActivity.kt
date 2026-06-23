package com.grabam.quicksettings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.grabam.service.DownloadService
import com.grabam.utils.UrlUtils

class TileDownloadActivity : AppCompatActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set a dummy view to ensure the Window Manager actually lays out the window and grants focus
        setContentView(android.view.View(this))
        
        // Fallback: If window focus is never granted (due to OS optimizations for transparent activities), 
        // we still execute after a short delay to guarantee we never hang the app or the tile.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !handled) {
                handled = true
                startDownloadFromClipboard()
                finish()
            }
        }, 600)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            startDownloadFromClipboard()
            finish()
        }
    }

    private fun startDownloadFromClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipboardText = readClipboardText(clipboard)
            val videoUrl = clipboardText?.let { UrlUtils.extractSupportedUrl(it) }

            if (videoUrl == null) {
                val message = if (clipboardText.isNullOrBlank()) {
                    "Copy a Twitter/X or YouTube link first"
                } else {
                    "No supported video URL found in clipboard"
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                return
            }

            val serviceIntent = Intent(this, DownloadService::class.java).apply {
                action = DownloadService.ACTION_START_DOWNLOAD
                putExtra(DownloadService.EXTRA_URL, videoUrl.url)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Toast.makeText(
                applicationContext,
                "Grabbing ${videoUrl.platform.displayName} video...",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Could not read clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readClipboardText(clipboard: ClipboardManager): String? {
        val clipData = clipboard.primaryClip ?: return null
        if (clipData.itemCount == 0) return null

        return buildString {
            for (index in 0 until clipData.itemCount) {
                val text = clipData.getItemAt(index).coerceToText(this@TileDownloadActivity)?.toString()
                if (!text.isNullOrBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(text)
                }
            }
        }.ifBlank { null }
    }
}
