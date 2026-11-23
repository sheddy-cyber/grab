package com.grab

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.grab.utils.DownloadLauncher

/**
 * Receives links from the system share sheet. Shows a small card (Android 14 requires a
 * visible activity to start a dataSync foreground service), starts the download, and closes.
 */
class ShareActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)
        findViewById<View>(android.R.id.content).setOnClickListener { finish() }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.let { it.getStringExtra(Intent.EXTRA_TEXT) ?: it.getStringExtra(Intent.EXTRA_SUBJECT) }

        when (val result = DownloadLauncher.start(this, shared.orEmpty())) {
            is DownloadLauncher.Result.Started ->
                show(getString(R.string.share_starting, result.platform.displayName), getString(R.string.share_hint), false, 1400)
            is DownloadLauncher.Result.AlreadyDownloading ->
                show(getString(R.string.already_downloading, result.platform.displayName), getString(R.string.share_hint), false, 1600)
            DownloadLauncher.Result.NoInternet ->
                show(getString(R.string.no_internet), getString(R.string.share_hint_retry), true, 2200)
            DownloadLauncher.Result.InvalidUrl ->
                show(getString(R.string.share_invalid_title), getString(R.string.invalid_url), true, 2600)
        }
    }

    private fun show(title: String, message: String, isError: Boolean, closeAfterMs: Long) {
        findViewById<TextView>(R.id.tvTitle).text = title
        findViewById<TextView>(R.id.tvMessage).text = message
        findViewById<CircularProgressIndicator>(R.id.progress).visibility = if (isError) View.GONE else View.VISIBLE
        findViewById<ImageView>(R.id.ivError).visibility = if (isError) View.VISIBLE else View.GONE
        findViewById<ImageView>(R.id.ivLogo).visibility = if (isError) View.GONE else View.VISIBLE
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, closeAfterMs)
    }
}
