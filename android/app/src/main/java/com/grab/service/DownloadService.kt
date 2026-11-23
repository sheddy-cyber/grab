package com.grab.service

import android.app.Notification
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.grab.R
import com.grab.data.DownloadStatus
import com.grab.data.DownloadStore
import com.grab.utils.BackendConfig
import com.grab.utils.ErrorMessages
import com.grab.utils.FileNames
import com.grab.utils.GrabException
import com.grab.utils.MediaFormatUtils
import com.grab.utils.NetworkUtils
import com.grab.utils.NotificationHelper
import com.grab.utils.ServerUnavailableException
import com.grab.utils.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    companion object {
        private const val TAG = "GrabDownload"
        const val ACTION_START_DOWNLOAD = "com.grab.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.grab.CANCEL_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_RECORD_ID = "extra_record_id"

        private const val MAX_EXTRACT_ATTEMPTS = 3
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val FREE_SPACE_MARGIN = 20L * 1024 * 1024

        private val nextId = AtomicInteger(0)
        private val activeDownloads = ConcurrentHashMap<Int, DownloadTask>()

        val isDownloading: Boolean get() = activeDownloads.isNotEmpty()

        private val ALLOWED_DOWNLOAD_HEADERS = setOf(
            "user-agent", "referer", "origin", "cookie", "accept", "accept-language"
        )

        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    private class DownloadTask(
        val url: String,
        val recordId: String,
        val label: String
    ) {
        @Volatile var isCancelled = false
        @Volatile var activeCall: Call? = null
        @Volatile var notification: Notification? = null
    }

    private data class Extraction(
        val downloadUrl: String,
        val title: String,
        val extension: String,
        val mimeType: String,
        val thumbnail: String?,
        val headers: Map<String, String>
    )

    private data class SavedFile(val uri: Uri, val mimeType: String, val size: Long)

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private lateinit var notificationHelper: NotificationHelper
    private var foregroundId = -1

    override fun onCreate() {
        super.onCreate()
        DownloadStore.init(this)
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> handleStart(intent.getStringExtra(EXTRA_URL))
            ACTION_CANCEL_DOWNLOAD -> {
                val byId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                val recordId = intent.getStringExtra(EXTRA_RECORD_ID)
                val entry = activeDownloads.entries.firstOrNull { (id, task) ->
                    id == byId || (recordId != null && task.recordId == recordId)
                }
                // The running coroutine notices the flag (or the aborted call) and cleans up itself,
                // so its finally-block always runs and the foreground state stays consistent.
                entry?.value?.let { task ->
                    task.isCancelled = true
                    task.activeCall?.cancel()
                }
                if (activeDownloads.isEmpty()) stopSelf()
            }
            else -> if (activeDownloads.isEmpty()) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(rawUrl: String?) {
        val video = rawUrl?.let(UrlUtils::extractSupportedUrl)
        val alreadyRunning = video != null && activeDownloads.values.any { it.url == video.url }
        if (video == null || alreadyRunning) {
            // startForegroundService() obliges us to call startForeground() even when we bail out.
            satisfyForegroundRequirement()
            return
        }

        // A retry clears the earlier "failed" notification for this link.
        notificationHelper.cancelNotification(video.url.hashCode())
        val record = DownloadStore.create(video.url, video.platform.displayName)
        val downloadId = NotificationHelper.NOTIFICATION_ID + nextId.incrementAndGet()
        val label = getString(R.string.platform_video, video.platform.displayName)
        val task = DownloadTask(video.url, record.id, label)
        activeDownloads[downloadId] = task

        val initialText = getString(R.string.status_fetching)
        DownloadStore.updateProgress(record.id) { it.copy(statusText = initialText) }
        val notification = notificationHelper
            .buildProgress(downloadId, label, initialText, 0, true).build()
        task.notification = notification
        val current = activeDownloads[foregroundId]?.notification
        if (foregroundId == -1 || current == null) {
            promoteForeground(downloadId, notification)
        } else {
            // Re-assert foreground for this start request; show the new download separately.
            promoteForeground(foregroundId, current)
            postNotification(downloadId, notification)
        }

        serviceScope.launch { runDownload(downloadId, task, video) }
    }

    private suspend fun runDownload(downloadId: Int, task: DownloadTask, video: UrlUtils.SupportedVideoUrl) {
        val recordId = task.recordId
        try {
            val info = extract(video, task, downloadId)
            if (task.isCancelled) throw kotlinx.coroutines.CancellationException("Cancelled")
            DownloadStore.update(recordId) {
                it.copy(title = info.title, thumbnailUrl = info.thumbnail ?: it.thumbnailUrl)
            }
            setStage(downloadId, task, info.title, getString(R.string.status_downloading), 0, true)

            val saved = downloadToGallery(info, task, downloadId)

            DownloadStore.update(recordId) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    fileUri = saved.uri.toString(),
                    mimeType = saved.mimeType,
                    totalBytes = saved.size,
                    bytesDownloaded = saved.size,
                    progress = 100,
                    statusText = null,
                    error = null
                )
            }
            notificationHelper.showComplete(task.url.hashCode(), info.title, saved.uri, saved.mimeType)
            toastIfNotificationsOff(getString(R.string.toast_saved, info.title))
        } catch (e: Throwable) {
            if (task.isCancelled) {
                Log.d(TAG, "Download cancelled by user")
                DownloadStore.remove(recordId)
            } else {
                Log.e(TAG, "Download failed", e)
                val message = ErrorMessages.from(e, NetworkUtils.isInternetAvailable(this))
                DownloadStore.update(recordId) {
                    it.copy(status = DownloadStatus.FAILED, error = message, statusText = null, progress = -1)
                }
                notificationHelper.showError(task.url.hashCode(), message, task.url)
                toastIfNotificationsOff(message)
            }
        } finally {
            withContext(Dispatchers.Main + NonCancellable) { onTaskFinished(downloadId) }
        }
    }

    // region Extraction

    private suspend fun extract(
        video: UrlUtils.SupportedVideoUrl,
        task: DownloadTask,
        downloadId: Int
    ): Extraction {
        val encoded = URLEncoder.encode(video.url, "UTF-8")
        val apiUrl = BackendConfig.extractUrl(this, encoded)
        Log.d(TAG, "Calling API: $apiUrl")

        var attempt = 1
        while (true) {
            try {
                return parseExtraction(requestExtraction(apiUrl, task), task.label)
            } catch (e: IOException) {
                // Timeouts and 502/503/504 usually mean a free-tier backend is still waking up.
                if (task.isCancelled || attempt >= MAX_EXTRACT_ATTEMPTS ||
                    !NetworkUtils.isInternetAvailable(this)
                ) throw e
                Log.w(TAG, "Extraction attempt $attempt failed, retrying", e)
                setStage(
                    downloadId, task, task.label,
                    getString(R.string.status_waking_server, attempt + 1, MAX_EXTRACT_ATTEMPTS),
                    0, true
                )
                delay(3_000L * attempt)
                if (task.isCancelled) throw e
                attempt++
            }
        }
    }

    private fun requestExtraction(apiUrl: String, task: DownloadTask): String {
        val call = httpClient.newCall(Request.Builder().url(apiUrl).build())
        task.activeCall = call
        call.execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code in 502..504) throw ServerUnavailableException(response.code)
            if (!response.isSuccessful) {
                val backendError = runCatching { JSONObject(body).optString("error") }.getOrNull()
                Log.w(TAG, "Backend error ${response.code}: $backendError")
                throw GrabException(ErrorMessages.fromBackend(backendError, response.code))
            }
            if (body.isBlank()) throw GrabException("The server sent an empty response. Please try again.")
            return body
        }
    }

    private fun parseExtraction(body: String, fallbackTitle: String): Extraction {
        val json = runCatching { JSONObject(body) }.getOrElse {
            throw GrabException("The server sent an unexpected response. Check the backend URL in settings.")
        }
        val downloadUrl = json.optString("download_url")
        if (downloadUrl.isBlank()) throw GrabException("No downloadable video was found in this post.")
        val extension = sanitizeExtension(json.optString("ext", "mp4"))
        return Extraction(
            downloadUrl = downloadUrl,
            title = json.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: fallbackTitle,
            extension = extension,
            mimeType = json.optString("mime_type").ifBlank { MediaFormatUtils.mimeTypeForExtension(extension) },
            thumbnail = json.optString("thumbnail").takeIf { it.startsWith("http") },
            headers = json.optJSONObject("headers").toStringMap()
        )
    }

    // endregion

    // region File download

    private suspend fun downloadToGallery(info: Extraction, task: DownloadTask, downloadId: Int): SavedFile {
        val requestBuilder = Request.Builder().url(info.downloadUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "video/webm,video/mp4,video/*;q=0.9,*/*;q=0.8")
        if (!info.downloadUrl.contains("/proxy?")) {
            info.headers.forEach { (key, value) ->
                if (key.lowercase() in ALLOWED_DOWNLOAD_HEADERS) requestBuilder.header(key, value)
            }
        }

        val call = httpClient.newCall(requestBuilder.build())
        task.activeCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) throw GrabException(ErrorMessages.forDownloadHttp(response.code))
            val body = response.body ?: throw GrabException("The server returned an empty file.")
            val totalBytes = body.contentLength()

            val header = response.peekBody(512).bytes()
            if (header.isEmpty()) {
                throw GrabException("The server returned an empty file. The video might be restricted.")
            }
            if (MediaFormatUtils.isLikelyErrorPayload(header)) {
                throw GrabException("The site blocked the download or the link expired. Please try again.")
            }
            val detected = MediaFormatUtils.detectFromHeader(header)
            val extension = detected?.extension ?: info.extension
            val mimeType = detected?.mimeType ?: info.mimeType

            ensureFreeSpace(totalBytes)

            // Stream straight into MediaStore: no temp copy, half the disk usage and no extra copy step.
            val uri = createGalleryEntry(info.title, extension, mimeType)
            try {
                val written = writeBody(body.byteStream(), uri, totalBytes, task, downloadId, info.title)
                if (totalBytes > 0 && written < totalBytes) throw IOException("Connection closed prematurely")
                setStage(downloadId, task, info.title, getString(R.string.status_saving), 100, false)
                publishGalleryEntry(uri, written)
                return SavedFile(uri, mimeType, written)
            } catch (t: Throwable) {
                runCatching { contentResolver.delete(uri, null, null) }
                throw t
            }
        }
    }

    private fun writeBody(
        input: java.io.InputStream,
        uri: Uri,
        totalBytes: Long,
        task: DownloadTask,
        downloadId: Int,
        title: String
    ): Long {
        var written = 0L
        val output = contentResolver.openOutputStream(uri)
            ?: throw GrabException("Couldn't save the video to your gallery.")
        output.use { out ->
            val buffer = ByteArray(64 * 1024)
            var lastUpdate = 0L
            var lastBytes = 0L
            var speed = 0L
            while (true) {
                if (task.isCancelled) throw kotlinx.coroutines.CancellationException("Cancelled")
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                written += read

                val now = System.currentTimeMillis()
                if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                    if (lastUpdate > 0) speed = (written - lastBytes) * 1000 / (now - lastUpdate)
                    lastUpdate = now
                    lastBytes = written
                    reportBytes(downloadId, task, title, written, totalBytes, speed)
                }
            }
        }
        return written
    }

    private fun reportBytes(downloadId: Int, task: DownloadTask, title: String, done: Long, total: Long, speed: Long) {
        val doneText = Formatter.formatShortFileSize(this, done)
        val speedText = if (speed > 0) " · ${Formatter.formatShortFileSize(this, speed)}/s" else ""
        if (total > 0) {
            val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
            val text = "$percent% · $doneText / ${Formatter.formatShortFileSize(this, total)}$speedText"
            DownloadStore.updateProgress(task.recordId) {
                it.copy(progress = percent, bytesDownloaded = done, totalBytes = total, statusText = text)
            }
            updateNotification(downloadId, task, title, text, percent, false)
        } else {
            val text = "$doneText$speedText"
            DownloadStore.updateProgress(task.recordId) {
                it.copy(progress = -1, bytesDownloaded = done, statusText = text)
            }
            updateNotification(downloadId, task, title, text, 0, true)
        }
    }

    private fun ensureFreeSpace(totalBytes: Long) {
        if (totalBytes <= 0) return
        @Suppress("DEPRECATION")
        val available = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }
            .getOrDefault(Long.MAX_VALUE)
        if (available < totalBytes + FREE_SPACE_MARGIN) {
            val needed = Formatter.formatShortFileSize(this, totalBytes)
            throw GrabException("Not enough storage space. This video needs $needed free.")
        }
    }

    private fun createGalleryEntry(title: String, extension: String, mimeType: String): Uri {
        val baseName = FileNames.sanitize(title)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.TITLE, title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // MediaStore de-duplicates names itself ("Title (1).mp4").
                put(MediaStore.Video.Media.DISPLAY_NAME, "$baseName.$extension")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/grab")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "grab")
                dir.mkdirs()
                var file = java.io.File(dir, "$baseName.$extension")
                var n = 1
                while (file.exists()) file = java.io.File(dir, "$baseName (${n++}).$extension")
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, file.absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        return contentResolver.insert(collection, values)
            ?: throw GrabException("Couldn't save the video to your gallery.")
    }

    private fun publishGalleryEntry(uri: Uri, size: Long) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.SIZE, size)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        contentResolver.update(uri, values, null, null)
    }

    // endregion

    // region Notifications & foreground state

    private fun setStage(downloadId: Int, task: DownloadTask, title: String, text: String, progress: Int, indeterminate: Boolean) {
        DownloadStore.updateProgress(task.recordId) {
            it.copy(statusText = text, progress = if (indeterminate) -1 else progress)
        }
        updateNotification(downloadId, task, title, text, progress, indeterminate)
    }

    private fun updateNotification(downloadId: Int, task: DownloadTask, title: String, text: String, progress: Int, indeterminate: Boolean) {
        if (task.isCancelled) return
        val notification = notificationHelper.buildProgress(downloadId, title, text, progress, indeterminate).build()
        task.notification = notification
        postNotification(downloadId, notification)
    }

    private fun postNotification(id: Int, notification: Notification) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun promoteForeground(id: Int, notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(id, notification)
            }
            foregroundId = id
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun satisfyForegroundRequirement() {
        val current = activeDownloads[foregroundId]?.notification
        if (current != null) {
            promoteForeground(foregroundId, current)
        } else {
            val placeholder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.app_name))
                .setSilent(true)
                .build()
            promoteForeground(NotificationHelper.NOTIFICATION_ID, placeholder)
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundId = -1
            stopSelf()
        }
    }

    /** Runs on the main thread so it is serialized with [onStartCommand]. */
    private fun onTaskFinished(downloadId: Int) {
        activeDownloads.remove(downloadId)
        if (activeDownloads.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundId = -1
            notificationHelper.cancelNotification(downloadId)
            stopSelf()
            return
        }
        if (foregroundId == downloadId) {
            val (nextId, nextTask) = activeDownloads.entries.first().toPair()
            nextTask.notification?.let { promoteForeground(nextId, it) }
        }
        notificationHelper.cancelNotification(downloadId)
    }

    private suspend fun toastIfNotificationsOff(message: String) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        withContext(Dispatchers.Main + NonCancellable) {
            Toast.makeText(this@DownloadService, message, Toast.LENGTH_LONG).show()
        }
    }

    // endregion

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        keys().forEach { key -> optString(key).takeIf { it.isNotBlank() }?.let { result[key] = it } }
        return result
    }

    private fun sanitizeExtension(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "mp4" }.take(8)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Not a user cancel: let running tasks fail as "interrupted" so they can be retried.
        activeDownloads.values.forEach { it.activeCall?.cancel() }
        serviceScope.cancel()
        super.onDestroy()
    }
}
