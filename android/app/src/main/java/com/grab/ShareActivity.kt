package com.grab

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.grab.service.DownloadService
import com.grab.utils.UrlUtils
import com.grab.utils.NetworkUtils

class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Android 14 requires the app to be in a visible foreground state to start a dataSync Foreground Service.
        // We create a minimal UI so this Activity isn't considered "transparent" or "background" by the OS.
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(android.graphics.Color.parseColor("#222222"))
            
            addView(android.widget.ProgressBar(this@ShareActivity))
            addView(android.widget.TextView(this@ShareActivity).apply {
                text = "Starting grab..."
                setTextColor(android.graphics.Color.WHITE)
                setPadding(0, 32, 0, 0)
            })
        }
        setContentView(layout)

        handleIntent(intent)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finish()
        }, 1000)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finish()
        }, 1000)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                val videoUrl = UrlUtils.extractSupportedUrl(sharedText)
                if (videoUrl != null) {
                    if (!NetworkUtils.isInternetAvailable(this)) {
                        Toast.makeText(this, R.string.no_internet, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
