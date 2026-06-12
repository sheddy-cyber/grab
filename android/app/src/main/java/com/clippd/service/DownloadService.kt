package com.clippd.service

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import com.clippd.utils.NotificationHelper
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStream
import java.net.URL

class DownloadService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.clippd.START_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        
        /**
         * Backend URL for video extraction.
         * 
         * You need to deploy a backend service that:
         * 1. Accepts a Twitter/X URL as a query parameter
         * 2. Extracts the actual video URL from the tweet
         * 3. Returns JSON with: {"download_url": "direct_video_url", "title": "video_title"}
         * 
         * Example backend implementation using yt-dlp or similar tools:
         * - Python with yt-dlp: https://github.com/yt-dlp/yt-dlp
         * - Node.js with twitter-scraper: https://github.com/erikzak/twitter-scraper
         * 
         * Replace with your actual backend URL when deployed.
         */
        const val BACKEND_URL = "http://YOUR_BACKEND_IP:8000/extract?url="
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val url = intent.getStringExtra(EXTRA_URL)
            if (url != null) {
                startForegroundDownload(url)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundDownload(videoUrl: String) {
        val builder = notificationHelper.getBaseNotification("Clippd", "Extracting video...")
        startForeground(NotificationHelper.NOTIFICATION_ID, builder.build())

        serviceScope.launch {
            try {
                // Validate URL
                if (!isValidTwitterUrl(videoUrl)) {
                    throw Exception("Invalid Twitter URL")
                }

                // 1. Extract real download URL from backend
                notificationHelper.updateProgress(builder, 10)
                
                val response = URL(BACKEND_URL + videoUrl).readText()
                val json = JSONObject(response)
                val downloadUrl = json.getString("download_url")
                val title = json.optString("title", "clippd_video_${System.currentTimeMillis()}")

                if (downloadUrl.isEmpty()) {
                    throw Exception("Failed to extract video URL")
                }

                // 2. Start the actual file download
                notificationHelper.updateProgress(builder, 30)
                downloadFile(downloadUrl, title, builder)
                
                notificationHelper.showComplete(title)
            } catch (e: Exception) {
                notificationHelper.showError(e.message ?: "Unknown error")
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun isValidTwitterUrl(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            
            host == "twitter.com" || 
            host == "www.twitter.com" || 
            host == "x.com" || 
            host == "www.x.com" ||
            host.endsWith(".twitter.com") ||
            host.endsWith(".x.com")
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun downloadFile(url: String, title: String, builder: androidx.core.app.NotificationCompat.Builder) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download file")

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            val inputStream: InputStream = body.byteStream()

            // 3. Save to MediaStore (Gallery)
            saveVideoToGallery(inputStream, title, contentLength) { progress ->
                notificationHelper.updateProgress(builder, progress)
            }
        }
    }

    private fun saveVideoToGallery(inputStream: InputStream, title: String, totalBytes: Long, onProgress: (Int) -> Unit) {
        val filename = "${title.replace(" ", "_")}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Clippd")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues) ?: throw Exception("Failed to create MediaStore entry")

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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
