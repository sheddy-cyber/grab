package com.grab.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.grab.data.DownloadStore
import com.grab.service.DownloadService

/** One place that validates a link and hands it to [DownloadService]. */
object DownloadLauncher {

    sealed class Result {
        data class Started(val platform: UrlUtils.Platform) : Result()
        data class AlreadyDownloading(val platform: UrlUtils.Platform) : Result()
        object InvalidUrl : Result()
        object NoInternet : Result()
    }

    fun start(context: Context, rawText: String): Result {
        val video = UrlUtils.extractSupportedUrl(rawText) ?: return Result.InvalidUrl
        if (!NetworkUtils.isInternetAvailable(context)) return Result.NoInternet

        DownloadStore.init(context)
        if (DownloadStore.findActive(video.url) != null) {
            return Result.AlreadyDownloading(video.platform)
        }

        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, video.url)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        return Result.Started(video.platform)
    }
}
