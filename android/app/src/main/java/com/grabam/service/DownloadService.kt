package com.grabam.service

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
import com.grabam.utils.UrlUtils
import com.grabam.utils.NotificationHelper
import com.grabam.utils.MediaFormatUtils
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
        const val ACTION_START_DOWNLOAD = "com.grabam.START_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        const val DEFAULT_BACKEND_URL = "https://sheddycyber-grab-am.hf.space"
        
        private val activeDownloadCount = java.util.concurrent.atomic.AtomicInteger(0)
        
        val isDownloading: Boolean
            get() = activeDownloadCount.get() > 0

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
            
            // Clean up if the user pasted the entire old-style URL with /extract?url=
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
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val url = intent.getStringExtra(EXTRA_URL)
            if (!url.isNullOrBlank()) {
                startForegroundDownload(url, startId)
            } else {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundDownload(videoUrl: String, startId: Int) {
        val downloadId = NotificationHelper.NOTIFICATION_ID + activeDownloadCount.incrementAndGet()
        Log.d("GrabAmDownload", "Starting foreground download for: $videoUrl with id $downloadId")

        val requestedVideo = UrlUtils.extractSupportedUrl(videoUrl)
        val initialContent = requestedVideo?.let {
            "Extracting ${it.platform.displayName} video..."
        } ?: "Checking link..."

        val builder = notificationHelper.getBaseNotification("grab am", initialContent)
        
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
            Log.d("GrabAmDownload", "Service in foreground. Notifying listeners.")
            onServiceStarted?.invoke()
        } catch (e: Exception) {
            Log.e("GrabAmDownload", "Error starting foreground service", e)
        }

        serviceScope.launch {
            
            var resultUri: android.net.Uri? = null
            var resultTitle: String? = null
            var resultMimeType: String? = null
            var errorMsg: String? = null

            try {
                val normalizedVideo = requestedVideo
                    ?: throw Exception("Unsupported link. Use a Twitter/X, YouTube, Facebook, or Instagram video URL.")
                Log.d("GrabAmDownload", "Normalized URL: ${normalizedVideo.url}")

                // 1. Extract real download URL from backend
                Log.d("GrabAmDownload", "Extracting video from backend...")
                notificationHelper.updateProgress(
                    builder,
                    10,
                    "Extracting ${normalizedVideo.platform.displayName} video...",
                    false,
                    downloadId
                )
                
                val encodedUrl = URLEncoder.encode(normalizedVideo.url, StandardCharsets.UTF_8.toString())
                val apiUrl = getFormattedApiUrl(this@DownloadService, encodedUrl)
                Log.d("GrabAmDownload", "Calling API: $apiUrl")
                
                val response = requestExtraction(apiUrl)
                
                Log.d("GrabAmDownload", "Backend response: $response")
                val json = JSONObject(response)
                val downloadUrl = json.getString("download_url")
                val title = json.optString("title", "grab_am_video_${System.currentTimeMillis()}")
                val extension = sanitizeExtension(json.optString("ext", "mp4"))
                val mimeType = json.optString("mime_type", "").ifBlank { mimeTypeForExtension(extension) }
                val headers = json.optJSONObject("headers").toStringMap()

                if (downloadUrl.isEmpty()) {
                    throw Exception("Failed to extract video URL")
                }

                // 2. Start the actual file download
                Log.d("GrabAmDownload", "Downloading file: $downloadUrl")
                notificationHelper.updateProgress(builder, 30, "Grabbing video...", false, downloadId)
                val videoUri = downloadFile(downloadUrl, title, extension, mimeType, headers, builder, downloadId)
                
                Log.d("GrabAmDownload", "Download complete! URI: $videoUri")
                resultUri = videoUri.first
                resultMimeType = videoUri.second
                resultTitle = title
            } catch (e: Exception) {
                Log.e("GrabAmDownload", "Download failed", e)
                errorMsg = e.message ?: "Unknown error"
            } finally {
                Log.d("GrabAmDownload", "Stopping service or finishing task")
                val remaining = activeDownloadCount.decrementAndGet()
                if (remaining == 0) {
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

                if (remaining == 0) {
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
            Log.e("GrabAmDownload", "Backend request failed", e)
            throw Exception("Failed to extract video: ${e.message}")
        }
    }

    private fun downloadFile(
        url: String,
        title: String,
        extension: String,
        mimeType: String,
        headers: Map<String, String>,
        builder: androidx.core.app.NotificationCompat.Builder,
        downloadId: Int
    ): Pair<android.net.Uri, String> {
        val requestBuilder = Request.Builder().url(url)
        
        val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        requestBuilder.header("User-Agent", defaultUserAgent)
        requestBuilder.header("Accept", "video/webm,video/mp4,video/*;q=0.9,*/*;q=0.8")

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
        
        return httpClient.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                val errorMessage = body?.string()?.let { raw ->
                    runCatching { JSONObject(raw).optString("error") }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                }
                throw Exception(
                    errorMessage ?: "Failed to download file: HTTP ${response.code}"
                )
            }
            
            val contentType = body.contentType()?.toString() ?: ""
            if (contentType.contains("text/html") || contentType.contains("application/json")) {
                throw Exception("Blocked by provider or invalid media format returned.")
            }

            val contentLength = body.contentLength()
            val headerBytes = response.peekBody(512).bytes()

            if (headerBytes.isEmpty()) {
                throw Exception(
                    "Download returned an empty file (HTTP ${response.code}, length=$contentLength)."
                )
            }

            if (MediaFormatUtils.isLikelyErrorPayload(headerBytes)) {
                throw Exception("Provider returned an error page instead of a video.")
            }

            val detectedFormat = MediaFormatUtils.detectFromHeader(headerBytes)
            val resolvedExtension = detectedFormat?.extension ?: extension.ifBlank { "mp4" }
            val resolvedMimeType = detectedFormat?.mimeType ?: mimeType.ifBlank { mimeTypeForExtension(resolvedExtension) }

            if (detectedFormat == null) {
                Log.w("GrabAmDownload", "Could not detect format from header bytes, using backend-provided: $extension / $mimeType")
            }

            saveVideoToGallery(body.byteStream(), title, resolvedExtension, resolvedMimeType, contentLength) { progress, downloadedBytes ->
                if (contentLength <= 0) {
                    val mb = downloadedBytes / (1024 * 1024)
                    notificationHelper.updateProgress(builder, 0, "Grabbing video... (${mb}MB)", true, downloadId)
                } else {
                    val downloadProgress = 30 + ((progress.coerceIn(0, 100) * 70) / 100)
                    notificationHelper.updateProgress(builder, downloadProgress, "Grabbing video... ($progress%)", false, downloadId)
                }
            }.let { uri -> Pair(uri, resolvedMimeType) }
        }
    }

    private fun saveVideoToGallery(
        inputStream: InputStream,
        title: String,
        extension: String,
        mimeType: String,
        totalBytes: Long,
        onProgress: (progress: Int, downloadedBytes: Long) -> Unit
    ): android.net.Uri {
        // Sanitize filename: remove special characters and limit length
        var sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
            .take(50)
            .trim('_')
        
        if (sanitizedTitle.isEmpty()) {
            sanitizedTitle = "video"
        }

        val filename = "${sanitizedTitle}_${System.currentTimeMillis()}.$extension"
        
        Log.d("GrabAmDownload", "Saving video with filename: $filename, mimeType: $mimeType, expected totalBytes: $totalBytes")
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/grab am")
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
        Log.d("GrabAmDownload", "Created MediaStore entry URI: $uri")

        try {
            var finalTotalRead: Long = 0
            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream == null) throw Exception("Failed to open output stream")
                
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastProgress = -1
                var lastUpdateTime = System.currentTimeMillis()
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    finalTotalRead += bytesRead
                    val currentTime = System.currentTimeMillis()
                    if (totalBytes > 0) {
                        val progress = ((finalTotalRead * 100) / totalBytes).toInt()
                        // Only update notification max twice a second, or if we hit 100%
                        if (progress != lastProgress && (currentTime - lastUpdateTime > 500 || progress == 100)) {
                            onProgress(progress, finalTotalRead)
                            lastProgress = progress
                            lastUpdateTime = currentTime
                        }
                    } else {
                        if (currentTime - lastUpdateTime > 500) {
                            onProgress(0, finalTotalRead)
                            lastUpdateTime = currentTime
                        }
                    }
                }
                outputStream.flush()
                Log.d("GrabAmDownload", "All stream bytes written to file. Total size: $finalTotalRead bytes")
            }

            if (finalTotalRead == 0L) {
                throw Exception("Download returned an empty file.")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                    put(MediaStore.Video.Media.SIZE, finalTotalRead)
                }
                val rows = resolver.update(uri, updateValues, null, null)
                Log.d("GrabAmDownload", "Updated MediaStore IS_PENDING to 0. Rows affected: $rows")
            } else {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Video.Media.SIZE, finalTotalRead)
                }
                resolver.update(uri, updateValues, null, null)
            }
        } catch (e: Exception) {
            Log.e("GrabAmDownload", "Exception inside saveVideoToGallery, deleting incomplete file: ${e.message}", e)
            resolver.delete(uri, null, null)
            throw e
        }
        
        Log.d("GrabAmDownload", "Video saved successfully to: $uri")
        return uri
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
