package com.grab

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.grab.data.DownloadRecord
import com.grab.data.DownloadStatus
import com.grab.data.DownloadStore
import com.grab.service.DownloadService
import com.grab.ui.DownloadAdapter
import com.grab.ui.ThumbnailLoader
import com.grab.utils.BackendConfig
import com.grab.utils.DownloadLauncher
import com.grab.utils.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), DownloadAdapter.Listener {

    companion object {
        const val ACTION_DOWNLOAD_CLIPBOARD = "com.grab.action.DOWNLOAD_CLIPBOARD"
        const val ACTION_RETRY = "com.grab.action.RETRY"
        private const val PREFS_UI = "grab_ui"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }

    private lateinit var root: View
    private lateinit var tilUrl: TextInputLayout
    private lateinit var etUrl: TextInputEditText
    private lateinit var btnDownload: MaterialButton
    private lateinit var recentHeader: View
    private lateinit var btnClear: View
    private lateinit var rvRecent: RecyclerView
    private lateinit var tipCard: View
    private lateinit var adapter: DownloadAdapter

    private var pendingClipboardDownload = false
    private var pendingRetryUrl: String? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        DownloadStore.init(this)

        root = findViewById(R.id.root)
        tilUrl = findViewById(R.id.tilUrl)
        etUrl = findViewById(R.id.etUrl)
        btnDownload = findViewById(R.id.btnDownload)
        recentHeader = findViewById(R.id.recentHeader)
        btnClear = findViewById(R.id.btnClear)
        rvRecent = findViewById(R.id.rvRecent)
        tipCard = findViewById(R.id.tipCard)

        setupInput()
        setupRecentList()
        setupCreatedByText()
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
        btnClear.setOnClickListener { confirmClearHistory() }

        requestStoragePermissionIfNeeded()
        if (DownloadStore.records.value.isNotEmpty()) requestNotificationPermissionOnce()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        pendingRetryUrl?.let {
            pendingRetryUrl = null
            launchDownload(it)
        }
        // Refresh relative timestamps ("5 min ago").
        if (adapter.itemCount > 0) adapter.notifyItemRangeChanged(0, adapter.itemCount, "time")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android 10+ only exposes the clipboard to the focused app.
        if (hasFocus && pendingClipboardDownload) {
            pendingClipboardDownload = false
            val text = readClipboard()
            if (text != null && UrlUtils.extractSupportedUrl(text) != null) {
                launchDownload(text)
            } else {
                snack(getString(R.string.clipboard_no_link))
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_DOWNLOAD_CLIPBOARD -> pendingClipboardDownload = true
            ACTION_RETRY -> pendingRetryUrl = intent.getStringExtra(DownloadService.EXTRA_URL)
            else -> return
        }
        // Consume the action so rotation doesn't repeat it.
        setIntent(Intent(this, MainActivity::class.java))
    }

    // region Input

    private fun setupInput() {
        etUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateInputState()
        })
        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                onPrimaryAction(); true
            } else false
        }
        tilUrl.setEndIconOnClickListener {
            if (etUrl.text.isNullOrEmpty()) pasteFromClipboard() else etUrl.text?.clear()
        }
        btnDownload.setOnClickListener { onPrimaryAction() }
        updateInputState()
    }

    private fun updateInputState() {
        val text = etUrl.text?.toString().orEmpty().trim()
        tilUrl.error = null
        val video = if (text.isEmpty()) null else UrlUtils.extractSupportedUrl(text)

        if (text.isEmpty()) {
            tilUrl.setEndIconDrawable(R.drawable.ic_paste)
            tilUrl.endIconContentDescription = getString(R.string.paste)
            btnDownload.setText(R.string.paste_link)
            btnDownload.setIconResource(R.drawable.ic_paste)
        } else {
            tilUrl.setEndIconDrawable(R.drawable.ic_close)
            tilUrl.endIconContentDescription = getString(R.string.clear)
            btnDownload.setText(R.string.download)
            btnDownload.setIconResource(R.drawable.ic_download)
        }

        val color = video?.let { ContextCompat.getColor(this, platformColor(it.platform)) }
            ?: ContextCompat.getColor(this, R.color.textMuted)
        tilUrl.setStartIconTintList(ColorStateList.valueOf(color))
        if (video != null) {
            tilUrl.helperText = getString(R.string.platform_detected, video.platform.displayName)
            tilUrl.setHelperTextColor(ColorStateList.valueOf(color))
        } else {
            tilUrl.helperText = null
        }
    }

    private fun onPrimaryAction() {
        val text = etUrl.text?.toString().orEmpty().trim()
        if (text.isEmpty()) {
            pasteFromClipboard()
        } else {
            launchDownload(text, fromInput = true)
        }
    }

    private fun pasteFromClipboard() {
        val text = readClipboard()
        if (text.isNullOrBlank()) {
            snack(getString(R.string.clipboard_empty))
            return
        }
        // Keep only the link if the clipboard holds a whole caption/message.
        val link = UrlUtils.extractSupportedUrl(text)
        etUrl.setText(link?.url ?: text.trim())
        etUrl.setSelection(etUrl.text?.length ?: 0)
        if (link == null) tilUrl.error = getString(R.string.invalid_url_short)
    }

    private fun readClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()
    }

    // endregion

    // region Downloads

    private fun launchDownload(text: String, fromInput: Boolean = false) {
        requestNotificationPermissionOnce()
        when (val result = DownloadLauncher.start(this, text)) {
            is DownloadLauncher.Result.Started -> {
                if (fromInput) {
                    etUrl.text?.clear()
                    hideKeyboard()
                }
                btnDownload.performHapticFeedback(HapticFeedbackConstants.CONFIRM.takeIf {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                } ?: HapticFeedbackConstants.VIRTUAL_KEY)
                findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll).post {
                    findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll)
                        .smoothScrollTo(0, (recentHeader.top - 24).coerceAtLeast(0))
                }
            }
            is DownloadLauncher.Result.AlreadyDownloading ->
                snack(getString(R.string.already_downloading, result.platform.displayName))
            DownloadLauncher.Result.InvalidUrl -> {
                if (fromInput) tilUrl.error = getString(R.string.invalid_url) else snack(getString(R.string.invalid_url))
            }
            DownloadLauncher.Result.NoInternet -> snack(getString(R.string.no_internet))
        }
    }

    private fun setupRecentList() {
        adapter = DownloadAdapter(lifecycleScope, ThumbnailLoader(this), this)
        rvRecent.layoutManager = LinearLayoutManager(this)
        rvRecent.adapter = adapter
        rvRecent.itemAnimator?.changeDuration = 0

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DownloadStore.records.collect { records ->
                    adapter.submitList(records)
                    val hasItems = records.isNotEmpty()
                    recentHeader.isVisible = hasItems
                    rvRecent.isVisible = hasItems
                    tipCard.isVisible = !hasItems
                    btnClear.isVisible = records.any { it.status != DownloadStatus.ACTIVE }
                }
            }
        }
    }

    override fun onOpen(record: DownloadRecord) {
        val uri = record.uri
        if (uri == null || !mediaExists(uri)) {
            Snackbar.make(root, R.string.file_missing, Snackbar.LENGTH_LONG)
                .setAction(R.string.download_again) {
                    DownloadStore.remove(record.id)
                    launchDownload(record.sourceUrl)
                }.show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, record.mimeType ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            snack(getString(R.string.no_player))
        }
    }

    override fun onRetry(record: DownloadRecord) {
        launchDownload(record.sourceUrl)
    }

    override fun onCancel(record: DownloadRecord) {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadService.EXTRA_RECORD_ID, record.id)
        }
        if (DownloadService.isDownloading) startService(intent) else DownloadStore.remove(record.id)
    }

    override fun onMore(record: DownloadRecord, anchor: View) {
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        when (record.status) {
            DownloadStatus.COMPLETED -> {
                menu.add(0, 1, 0, R.string.play)
                menu.add(0, 2, 1, R.string.share)
            }
            DownloadStatus.FAILED -> menu.add(0, 3, 0, R.string.retry)
            DownloadStatus.ACTIVE -> menu.add(0, 6, 3, R.string.cancel)
        }
        menu.add(0, 4, 2, R.string.copy_link)
        if (record.status != DownloadStatus.ACTIVE) menu.add(0, 5, 3, R.string.remove_from_list)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> onOpen(record)
                2 -> shareVideo(record)
                3 -> onRetry(record)
                4 -> copyLink(record)
                5 -> DownloadStore.remove(record.id)
                6 -> onCancel(record)
            }
            true
        }
        popup.show()
    }

    private fun shareVideo(record: DownloadRecord) {
        val uri = record.uri
        if (uri == null || !mediaExists(uri)) {
            snack(getString(R.string.file_missing))
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType ?: "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_video)))
    }

    private fun copyLink(record: DownloadRecord) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("link", record.sourceUrl))
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snack(getString(R.string.link_copied))
    }

    private fun mediaExists(uri: Uri): Boolean = runCatching {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ -> DownloadStore.clearFinished() }
            .show()
    }

    // endregion

    // region Settings

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val til = view.findViewById<TextInputLayout>(R.id.tilBackend)
        val input = view.findViewById<TextInputEditText>(R.id.etBackend)
        val btnTest = view.findViewById<MaterialButton>(R.id.btnTest)
        val progress = view.findViewById<CircularProgressIndicator>(R.id.testProgress)
        val result = view.findViewById<TextView>(R.id.tvTestResult)
        input.setText(BackendConfig.rawUrl(this))

        btnTest.setOnClickListener {
            val url = input.text?.toString().orEmpty()
            if (!BackendConfig.isValid(url)) {
                til.error = getString(R.string.invalid_backend_url)
                return@setOnClickListener
            }
            til.error = null
            btnTest.isEnabled = false
            progress.isVisible = true
            result.isVisible = true
            result.setTextColor(ContextCompat.getColor(this, R.color.textSecondary))
            result.setText(R.string.testing)
            lifecycleScope.launch {
                val error = withContext(Dispatchers.IO) { pingBackend(url) }
                btnTest.isEnabled = true
                progress.isVisible = false
                if (error == null) {
                    result.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.success))
                    result.setText(R.string.connection_ok)
                } else {
                    result.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error))
                    result.text = getString(R.string.connection_failed, error)
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.reset) { _, _ ->
                BackendConfig.save(this, BackendConfig.DEFAULT_BACKEND_URL)
                snack(getString(R.string.settings_reset))
            }
            .show()

        // Validate before closing.
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = input.text?.toString().orEmpty().trim()
            if (!BackendConfig.isValid(url)) {
                til.error = getString(R.string.invalid_backend_url)
            } else {
                BackendConfig.save(this, BackendConfig.normalize(url))
                snack(getString(R.string.settings_saved))
                dialog.dismiss()
            }
        }
    }

    private fun pingBackend(url: String): String? = try {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(BackendConfig.healthUrl(url)).build()).execute().use {
            if (it.isSuccessful) null else "HTTP ${it.code}"
        }
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    // endregion

    // region Misc

    private fun setupCreatedByText() {
        val tvCreatedBy = findViewById<TextView>(R.id.tvCreatedBy)
        val text = getString(R.string.created_by)
        val name = "Kris Shedrach"
        val start = text.indexOf(name)
        val spannable = SpannableString(text)
        if (start >= 0) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://krisshedrach.dev"))) }
                }
            }, start, start + name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tvCreatedBy.text = spannable
        tvCreatedBy.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 102)
        }
    }

    /** Asks for notifications in context (first download) instead of on first launch. */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hideKeyboard() {
        etUrl.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(etUrl.windowToken, 0)
    }

    private fun snack(message: String) {
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun platformColor(platform: UrlUtils.Platform): Int = when (platform) {
        UrlUtils.Platform.YOUTUBE -> R.color.brandYouTube
        UrlUtils.Platform.TWITTER_X -> R.color.brandTwitter
        UrlUtils.Platform.INSTAGRAM -> R.color.brandInstagramEnd
        UrlUtils.Platform.FACEBOOK -> R.color.brandFacebook
    }

    // endregion
}
