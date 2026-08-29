package com.grab.service

import android.app.Service
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import com.grab.utils.UrlUtils
import com.grab.utils.NotificationHelper
import com.grab.utils.MediaFormatUtils
import com.grab.utils.NetworkUtils
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class DownloadService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.grab.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.grab.CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.grab.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.grab.RESUME_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val DEFAULT_BACKEND_URL = "https://grab-am.onrender.com"
        
        private val activeDownloadCount = java.util.concurrent.atomic.AtomicInteger(0)
        
        class DownloadTask(
            val url: String,
            var isPaused: Boolean = false,
            var isCancelled: Boolean = false,
            var job: Job? = null,
            var builder: androidx.core.app.NotificationCompat.Builder? = null
        )
        
        val activeDownloads = java.util.concurrent.ConcurrentHashMap<Int, DownloadTask>()
        
        val isDownloading: Boolean
            get() = activeDownloads.isNotEmpty()

        @Volatile
        var onServiceStarted: (() -> Unit)? = null

        private val ALLOWED_DOWNLOAD_HEADERS = setOf(
            "user-agent",
            "referer",
            "origin",
            "cookie",
            "accept",
            "accept-language"
        )

        private fun isProxiedDownload(url: String): Boolean = url.contains("/proxy?")

        fun getFormattedApiUrl(context: Context, encodedUrl: String): String {
            val sharedPrefs = context.getSharedPreferences("grab_am_settings", MODE_PRIVATE)
            var baseUrl = sharedPrefs.getString("backend_url", DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
            baseUrl = baseUrl.trim()
            
            if (baseUrl.contains("/extract?url=")) {
                baseUrl = baseUrl.substringBefore("/extract?url=")
            } else if (baseUrl.contains("/extract")) {
                baseUrl = baseUrl.substringBefore("/extract")
            }
            
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.dropLast(1)
            }
            
            return "$baseUrl/extract?url=$encodedUrl"
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.MINUTES)
        .build()
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (!url.isNullOrBlank()) {
                    startForegroundDownload(url, startId)
                } else if (activeDownloads.isEmpty()) {
                    stopSelf(startId)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val id = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                activeDownloads[id]?.let {
                    it.isCancelled = true
                    it.job?.cancel()
                    activeDownloads.remove(id)
                    notificationHelper.cancelNotification(id)
                    checkStopService(startId)
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val id = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                activeDownloads[id]?.let {
                    it.isPaused = true
                    it.builder?.let { builder ->
                        notificationHelper.updateProgress(builder, 0, "Paused", true, id, true)
                    }
                }
            }
            ACTION_RESUME_DOWNLOAD -> {
                val id = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                activeDownloads[id]?.let {
                    it.isPaused = false
                    it.builder?.let { builder ->
                        notificationHelper.updateProgress(builder, 0, "Resuming...", true, id, false)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }
    
    private fun checkStopService(startId: Int) {
        if (activeDownloads.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startForegroundDownload(videoUrl: String, startId: Int) {
        val downloadId = NotificationHelper.NOTIFICATION_ID + activeDownloadCount.incrementAndGet()
        Log.d("GrabDownload", "Starting foreground download for: $videoUrl with id $downloadId")

        val requestedVideo = UrlUtils.extractSupportedUrl(videoUrl)
        val initialContent = requestedVideo?.let {
            "Extracting ${it.platform.displayName} video..."
        } ?: "Checking link..."

        val builder = notificationHelper.getBaseNotification("grab", initialContent)
        
        val task = DownloadTask(videoUrl, builder = builder)
        activeDownloads[downloadId] = task
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+ requires specific foreground service types
                startForeground(
                    downloadId,
                    builder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(downloadId, builder.build())
            }
            // Notify active listeners that the service has successfully entered the foreground
            Log.d("GrabDownload", "Service in foreground. Notifying listeners.")
            onServiceStarted?.invoke()
        } catch (e: Exception) {
            Log.e("GrabDownload", "Error starting foreground service", e)
        }

        task.job = serviceScope.launch {
            
            var resultUri: android.net.Uri? = null
            var resultTitle: String? = null
            var resultMimeType: String? = null
            var errorMsg: String? = null

            try {
                val normalizedVideo = requestedVideo
                    ?: throw Exception("Unsupported link. Use a Twitter/X, YouTube, Facebook, or Instagram video URL.")
                Log.d("GrabDownload", "Normalized URL: ${normalizedVideo.url}")

                // 1. Extract real download URL from backend
                Log.d("GrabDownload", "Extracting video from backend...")
                notificationHelper.updateProgress(
                    builder,
                    10,
                    "Extracting ${normalizedVideo.platform.displayName} video...",
                    false,
                    downloadId
                )
                
                val encodedUrl = URLEncoder.encode(normalizedVideo.url, StandardCharsets.UTF_8.toString())
                val apiUrl = getFormattedApiUrl(this@DownloadService, encodedUrl)
                Log.d("GrabDownload", "Calling API: $apiUrl")
                
                val response = requestExtraction(apiUrl)
                
                Log.d("GrabDownload", "Backend response: $response")
                val json = JSONObject(response)
                val downloadUrl = json.getString("download_url")
                val title = json.optString("title", "grab_video_${System.currentTimeMillis()}")
                val extension = sanitizeExtension(json.optString("ext", "mp4"))
                val mimeType = json.optString("mime_type", "").ifBlank { mimeTypeForExtension(extension) }
                val headers = json.optJSONObject("headers").toStringMap()

                if (downloadUrl.isEmpty()) {
                    throw Exception("Failed to extract video URL")
                }

                // 2. Start the actual file download
                Log.d("GrabDownload", "Downloading file: $downloadUrl")
                notificationHelper.updateProgress(builder, 30, "grabbing video...", false, downloadId)
                val videoUri = downloadFile(downloadUrl, title, extension, mimeType, headers, builder, downloadId)
                
                Log.d("GrabDownload", "Download complete! URI: $videoUri")
                resultUri = videoUri.first
                resultMimeType = videoUri.second
                resultTitle = title
            } catch (e: Exception) {
                if (e is CancellationException || activeDownloads[downloadId]?.isCancelled == true) {
                    Log.d("GrabDownload", "Download cancelled by user")
                    // Do not show error notification if cancelled
                    return@launch
                }
                Log.e("GrabDownload", "Download failed", e)
                errorMsg = if (!NetworkUtils.isInternetAvailable(this@DownloadService)) {
                    "No internet connection. Please check your network."
                } else {
                    val rawMsg = e.message ?: e.toString()
                    when {
                        rawMsg.contains("timeout", ignoreCase = true) -> "Connection timed out. The server took too long to respond."
                        rawMsg.contains("UnknownHost", ignoreCase = true) -> "Could not connect to the server. Please check your internet."
                        rawMsg.contains("HTTP 403") || rawMsg.contains("HTTP 401") -> "Access denied. The link might be private or expired."
                        rawMsg.contains("HTTP 404") -> "Video not found. It may have been deleted."
                        rawMsg.contains("HTTP 429") -> "Too many requests. Please try again later."
                        rawMsg.contains("space") && rawMsg.contains("storage", ignoreCase = true) -> "Not enough storage space on your device."
                        rawMsg.contains("Unsupported link") -> "This link format is not supported yet."
                        rawMsg.contains("Empty response") -> "The server returned an empty file. The video might be restricted."
                        rawMsg.contains("error page") -> "The provider blocked the download or the link is invalid."
                        e is java.net.SocketException || e is java.io.IOException -> "Network connection was interrupted."
                        else -> "An unexpected error occurred. Please try again."
                    }
                }
            } finally {
                Log.d("GrabDownload", "Stopping service or finishing task")
                activeDownloads.remove(downloadId)
                
                if (activeDownloads.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                
                if (errorMsg != null) {
                    notificationHelper.showError(errorMsg, downloadId)
                } else if (resultTitle != null) {
                    notificationHelper.showComplete(resultTitle, resultUri, resultMimeType ?: "video/mp4", downloadId)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@DownloadService, "Download completed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                if (activeDownloads.isEmpty()) {
                    stopSelf()
                }
            }
        }
    }

    private fun requestExtraction(apiUrl: String): String {
        val request = Request.Builder().url(apiUrl).build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching { JSONObject(body).optString("error") }.getOrNull()
                    throw Exception(error?.takeIf { it.isNotBlank() } ?: "Backend returned HTTP ${response.code}")
                }
                body.ifBlank { throw Exception("Backend returned an empty response") }
            }
        } catch (e: Exception) {
            Log.e("GrabDownload", "Backend request failed", e)
            throw Exception("Failed to extract video: ${e.message}")
        }
    }

    private suspend fun downloadFile(
        url: String,
        title: String,
        extension: String,
        mimeType: String,
        headers: Map<String, String>,
        builder: androidx.core.app.NotificationCompat.Builder,
        downloadId: Int
    ): Pair<android.net.Uri, String> {
        val tempFile = java.io.File(cacheDir, "grab_part_${System.currentTimeMillis()}_$downloadId.tmp")
        var totalBytes = -1L
        var downloadedBytes = 0L
        
        var resolvedExtension = extension.ifBlank { "mp4" }
        var resolvedMimeType = mimeType.ifBlank { mimeTypeForExtension(resolvedExtension) }
        var formatDetected = false

        while (true) {
            try {
                val requestBuilder = Request.Builder().url(url)
                val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                requestBuilder.header("User-Agent", defaultUserAgent)
                requestBuilder.header("Accept", "video/webm,video/mp4,video/*;q=0.9,*/*;q=0.8")

                if (downloadedBytes > 0) {
                    requestBuilder.header("Range", "bytes=$downloadedBytes-")
                }

                if (!isProxiedDownload(url)) {
                    headers.forEach { (name, value) ->
                        if (name.isNotBlank() && value.isNotBlank() &&
                            name.lowercase() in ALLOWED_DOWNLOAD_HEADERS
                        ) {
                            requestBuilder.header(name, value)
                        }
                    }
                }

                val request = requestBuilder.build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 416 && downloadedBytes > 0) {
                            // Range Not Satisfiable might mean we are done
                            return@use
                        }
                        val errorMessage = response.body?.string()?.let { raw ->
                            runCatching { JSONObject(raw).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
                        }
                        throw Exception(errorMessage ?: "Failed to download file: HTTP ${response.code}")
                    }
                    
                    val isPartial = response.code == 206
                    val shouldAppend = downloadedBytes > 0 && isPartial
                    
                    if (downloadedBytes > 0 && !isPartial) {
                        // Server ignored Range header. We must restart the download.
                        downloadedBytes = 0L
                        totalBytes = -1L
                        formatDetected = false
                    }
                    
                    val body = response.body ?: throw Exception("Empty response body")
                    
                    if (totalBytes == -1L) {
                        val contentLength = body.contentLength()
                        if (contentLength > 0) {
                            totalBytes = if (isPartial) downloadedBytes + contentLength else contentLength
                        }
                    }

                    if (!formatDetected) {
                        val contentType = body.contentType()?.toString() ?: ""
                        if (contentType.contains("text/html") || contentType.contains("application/json")) {
                            throw Exception("Blocked by provider or invalid media format returned.")
                        }

                        val headerBytes = response.peekBody(512).bytes()
                        if (headerBytes.isEmpty() && downloadedBytes == 0L) {
                            throw Exception("Download returned an empty file (HTTP ${response.code}).")
                        }

                        if (MediaFormatUtils.isLikelyErrorPayload(headerBytes)) {
                            throw Exception("Provider returned an error page instead of a video.")
                        }

                        val detectedFormat = MediaFormatUtils.detectFromHeader(headerBytes)
                        resolvedExtension = detectedFormat?.extension ?: resolvedExtension
                        resolvedMimeType = detectedFormat?.mimeType ?: resolvedMimeType
                        formatDetected = true
                    }
                    
                    java.io.FileOutputStream(tempFile, shouldAppend).use { outputStream ->
                        val inputStream = body.byteStream()
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastProgress = -1
                        var lastUpdateTime = System.currentTimeMillis()
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!currentCoroutineContext().isActive || activeDownloads[downloadId]?.isCancelled == true) {
                                throw CancellationException("Service stopped")
                            }
                            
                            while (activeDownloads[downloadId]?.isPaused == true) {
                                delay(1000)
                                if (!currentCoroutineContext().isActive || activeDownloads[downloadId]?.isCancelled == true) {
                                    throw CancellationException("Service stopped")
                                }
                            }
                            
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            val currentTime = System.currentTimeMillis()
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                if (progress != lastProgress && (currentTime - lastUpdateTime > 500 || progress == 100)) {
                                    val downloadProgress = 30 + ((progress.coerceIn(0, 100) * 70) / 100)
                                    notificationHelper.updateProgress(builder, downloadProgress, "grabbing video... ($progress%)", false, downloadId)
                                    lastProgress = progress
                                    lastUpdateTime = currentTime
                                }
                            } else {
                                if (currentTime - lastUpdateTime > 500) {
                                    val mb = downloadedBytes / (1024 * 1024)
                                    notificationHelper.updateProgress(builder, 0, "grabbing video... (${mb}MB)", true, downloadId)
                                    lastUpdateTime = currentTime
                                }
                            }
                        }
                        outputStream.flush()
                    }
                }
                
                if (totalBytes != -1L && downloadedBytes < totalBytes) {
                    throw java.io.IOException("Connection closed prematurely")
                }
                
                // Finished successfully
                break

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                
                val isNetworkError = e is java.io.IOException || e is java.net.SocketException || e.message?.contains("timeout", ignoreCase = true) == true
                if (!isNetworkError) {
                    throw e // Fatal error, fail the download
                }

                // Pause the download on network error instead of auto-retrying
                activeDownloads[downloadId]?.isPaused = true
                
                if (totalBytes > 0) {
                    val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                    val downloadProgress = 30 + ((progress.coerceIn(0, 100) * 70) / 100)
                    notificationHelper.updateProgress(builder, downloadProgress, "Connection lost. Paused.", false, downloadId, true)
                } else {
                    notificationHelper.updateProgress(builder, 0, "Connection lost. Paused.", true, downloadId, true)
                }

                // Wait until the user manually resumes (or cancels)
                while (activeDownloads[downloadId]?.isPaused == true) {
                    delay(1000)
                    if (!currentCoroutineContext().isActive || activeDownloads[downloadId]?.isCancelled == true) {
                        throw CancellationException("Service stopped")
                    }
                }
            }
        }
        
        return moveFileToGallery(tempFile, title, resolvedExtension, resolvedMimeType)
    }

    private fun moveFileToGallery(
        tempFile: java.io.File,
        title: String,
        extension: String,
        mimeType: String
    ): Pair<android.net.Uri, String> {
        var sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_").take(50).trim('_')
        if (sanitizedTitle.isEmpty()) sanitizedTitle = "video"
        val filename = "${sanitizedTitle}_${System.currentTimeMillis()}.$extension"
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/grab")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues) ?: throw Exception("Failed to create MediaStore entry")

        try {
            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream == null) throw Exception("Failed to open output stream")
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            val fileSize = tempFile.length()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                    put(MediaStore.Video.Media.SIZE, fileSize)
                }
                resolver.update(uri, updateValues, null, null)
            } else {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Video.Media.SIZE, fileSize)
                }
                resolver.update(uri, updateValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        } finally {
            tempFile.delete()
        }
        
        return Pair(uri, mimeType)
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()

        val result = mutableMapOf<String, String>()
        keys().forEach { key ->
            val value = optString(key)
            if (value.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }

    private fun sanitizeExtension(rawExtension: String): String {
        return rawExtension
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "mp4" }
            .take(8)
    }

    private fun mimeTypeForExtension(extension: String): String {
        return MediaFormatUtils.mimeTypeForExtension(extension)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
