package com.grab.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.grab.data.DownloadRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tiny thumbnail loader (no extra dependency): prefers the saved video's own frame,
 * falls back to the thumbnail URL the backend returned (HTTP-cached on disk).
 */
class ThumbnailLoader(context: Context) {
    private val appContext = context.applicationContext
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val client = OkHttpClient.Builder()
        .cache(Cache(File(appContext.cacheDir, "thumbs"), 15L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun keyFor(record: DownloadRecord): String? =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) record.fileUri else null)
            ?: record.thumbnailUrl

    fun cached(key: String): Bitmap? = memory.get(key)

    suspend fun load(record: DownloadRecord): Bitmap? = withContext(Dispatchers.IO) {
        val key = keyFor(record) ?: return@withContext null
        memory.get(key)?.let { return@withContext it }
        val bitmap = fromFile(record) ?: record.thumbnailUrl?.let(::fromUrl)
        bitmap?.also { memory.put(key, it) }
    }

    private fun fromFile(record: DownloadRecord): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val uri = record.uri ?: return null
        return runCatching { appContext.contentResolver.loadThumbnail(uri, Size(320, 240), null) }.getOrNull()
    }

    private fun fromUrl(url: String): Bitmap? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 320 && bounds.outHeight / (sample * 2) >= 180) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}
