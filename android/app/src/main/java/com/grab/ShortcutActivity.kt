package com.grab

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.grab.service.DownloadService
import com.grab.utils.ForegroundAppDetector
import com.grab.utils.NotificationHelper
import com.grab.utils.UrlUtils
import com.grab.utils.NetworkUtils

/**
 * Invisible activity that reads the clipboard, starts DownloadService, and
 * restores the app the user was previously viewing.
 */
class ShortcutActivity : Activity() {

    private var handled = false
    private var finishing = false
    private var returnToPackage: String? = null

    private val finishRunnable = Runnable { finishGracefully() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure transparent styling
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        try {
            returnToPackage = ForegroundAppDetector.detectPreviousApp(this)
            
            // Wait slightly for window focus to stabilize
            window.decorView.postDelayed({
                if (!handled) {
                    downloadFromClipboard()
                }
            }, 100)
            
            // Failsafe: force finish after 3 seconds
            window.decorView.postDelayed(finishRunnable, 3000)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finishGracefully()
        }
    }

    private fun downloadFromClipboard() {
        if (handled) return
        handled = true
        
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            
            if (text.isNullOrBlank()) {
                val notificationHelper = NotificationHelper(this)
                notificationHelper.showMessage(
                    getString(R.string.app_name),
                    getString(R.string.clipboard_empty)
                )
                finishGracefully()
                return
            }
            
            val videoUrl = UrlUtils.extractSupportedUrl(text)
            if (videoUrl == null) {
                val notificationHelper = NotificationHelper(this)
                notificationHelper.showMessage(
                    getString(R.string.app_name),
                    getString(R.string.invalid_url)
                )
                finishGracefully()
                return
            }

            if (!NetworkUtils.isInternetAvailable(this)) {
                val notificationHelper = NotificationHelper(this)
                notificationHelper.showMessage(
                    getString(R.string.app_name),
                    getString(R.string.no_internet)
                )
                finishGracefully()
                return
            }

            DownloadService.onServiceStarted = {
                runOnUiThread { finishGracefully() }
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

            window?.decorView?.postDelayed(finishRunnable, 3000)
        } catch (e: Exception) {
            Log.e(TAG, "Shortcut download failed", e)
            val notificationHelper = NotificationHelper(this)
            notificationHelper.showMessage(getString(R.string.app_name), e.message ?: "Unknown error")
            finishGracefully()
        }
    }

    private fun finishGracefully() {
        if (finishing || isFinishing) return
        finishing = true
        DownloadService.onServiceStarted = null
        window?.decorView?.removeCallbacks(finishRunnable)
        overridePendingTransition(0, 0)

        ForegroundAppDetector.restorePreviousApp(applicationContext, returnToPackage)
        finishAndRemoveTask()
    }

    companion object {
        private const val TAG = "GrabShortcut"
    }
}
