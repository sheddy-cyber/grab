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
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
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
