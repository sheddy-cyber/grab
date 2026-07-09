package com.grabam

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.grabam.service.DownloadService
import com.grabam.utils.ForegroundAppDetector
import com.grabam.utils.NotificationHelper
import com.grabam.utils.UrlUtils

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
        // Capture the foreground app before this activity fully takes focus.
        returnToPackage = ForegroundAppDetector.detectPreviousApp(this)

        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
    }

    override fun onResume() {
        super.onResume()
        scheduleClipboardRead()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            scheduleClipboardRead()
        }
    }

    private fun scheduleClipboardRead() {
        if (handled || finishing) return

        window?.decorView?.post {
            if (handled || finishing || !hasWindowFocus()) return@post
            handled = true
            downloadFromClipboard()
        }
    }

    private fun downloadFromClipboard() {
        val notificationHelper = NotificationHelper(applicationContext)

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.trim()

            if (text.isNullOrEmpty()) {
                notificationHelper.showMessage(
                    getString(R.string.app_name),
                    getString(R.string.clipboard_empty)
                )
                finishGracefully()
                return
            }

            val videoUrl = UrlUtils.extractSupportedUrl(text)
            if (videoUrl == null) {
                notificationHelper.showMessage(
                    getString(R.string.app_name),
                    getString(R.string.invalid_url)
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
            notificationHelper.showError(e.message ?: "Unknown error")
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
        private const val TAG = "GrabAmShortcut"
    }
}
