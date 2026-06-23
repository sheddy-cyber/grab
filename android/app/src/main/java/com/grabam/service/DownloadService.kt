package com.grabam.service

import android.app.Service
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
        const val DEFAULT_BACKEND_URL = "http://192.168.43.26:8000"

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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
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
        Log.d("GrabAmDownload", "Starting foreground download for: $videoUrl")

        val requestedVideo = UrlUtils.extractSupportedUrl(videoUrl)
        val initialContent = requestedVideo?.let {
            "Extracting ${it.platform.displayName} video..."
        } ?: "Checking link..."

        val builder = notificationHelper.getBaseNotification("grab am", initialContent)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+ requires specific foreground service types
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    builder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID, builder.build())
            }
        } catch (e: Exception) {
            Log.e("GrabAmDownload", "Error starting foreground service", e)
        }

        serviceScope.launch {
            var resultUri: android.net.Uri? = null
            var resultTitle: String? = null
            var errorMsg: String? = null

            try {
                val normalizedVideo = requestedVideo
                    ?: throw Exception("Unsupported link. Use a Twitter/X or YouTube video URL.")
                Log.d("GrabAmDownload", "Normalized URL: ${normalizedVideo.url}")

                // 1. Extract real download URL from backend
                Log.d("GrabAmDownload", "Extracting video from backend...")
                notificationHelper.updateProgress(
                    builder,
                    10,
                    "Extracting ${normalizedVideo.platform.displayName} video..."
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
                notificationHelper.updateProgress(builder, 30, "Grabbing video...")
                val videoUri = downloadFile(downloadUrl, title, extension, mimeType, headers, builder)
                
                Log.d("GrabAmDownload", "Download complete! URI: $videoUri")
                resultUri = videoUri
                resultTitle = title
            } catch (e: Exception) {
                Log.e("GrabAmDownload", "Download failed", e)
                errorMsg = e.message ?: "Unknown error"
            } finally {
                Log.d("GrabAmDownload", "Stopping service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                
                if (errorMsg != null) {
                    notificationHelper.showError(errorMsg)
                } else if (resultTitle != null) {
                    notificationHelper.showComplete(resultTitle, resultUri)
                }

                stopSelf(startId)
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
        builder: androidx.core.app.NotificationCompat.Builder
    ): android.net.Uri? {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                requestBuilder.header(name, value)
            }
        }
        val request = requestBuilder.build()
        
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download file: HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            val inputStream: InputStream = body.byteStream()
            val resolvedMimeType = body.contentType()?.toString()
                ?.substringBefore(";")
                ?.takeIf { it.startsWith("video/") }
                ?: mimeType

            // 3. Save to MediaStore (Gallery)
            saveVideoToGallery(inputStream, title, extension, resolvedMimeType, contentLength) { progress ->
                val downloadProgress = 30 + ((progress.coerceIn(0, 100) * 70) / 100)
                notificationHelper.updateProgress(builder, downloadProgress, "Grabbing video...")
            }
        }
    }

    private fun saveVideoToGallery(
        inputStream: InputStream,
        title: String,
        extension: String,
        mimeType: String,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ): android.net.Uri {
        // Sanitize filename: remove special characters and limit length
        var sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
            .take(50)
            .trim('_')
        
        if (sanitizedTitle.isEmpty()) {
            sanitizedTitle = "video"
        }

        val filename = "${sanitizedTitle}_${System.currentTimeMillis()}.$extension"
        
        Log.d("GrabAmDownload", "Saving video with filename: $filename")
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

        try {
            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream == null) throw Exception("Failed to open output stream")
                
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead: Long = 0
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((totalRead * 100) / totalBytes).toInt()
                        onProgress(progress)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
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
        return when (extension.lowercase()) {
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
