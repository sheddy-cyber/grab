package com.grab.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.grab.MainActivity
import com.grab.R
import com.grab.service.DownloadService

/**
 * Uses standard notification templates so progress, actions and dark mode
 * all look native on every Android version.
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "grab_downloads"
        const val RESULT_CHANNEL_ID = "grab_results"
        const val NOTIFICATION_ID = 1001
    }

    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Download progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress of video downloads"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(RESULT_CHANNEL_ID, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a video is saved or a download fails"
                setSound(null, null)
            }
        )
    }

    fun buildProgress(
        downloadId: Int,
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setColor(ContextCompat.getColor(context, R.color.colorAccent))
            .setContentIntent(openAppIntent(downloadId * 4))
            .addAction(0, context.getString(R.string.cancel), cancelIntent(downloadId))
    }

    fun updateProgress(
        downloadId: Int,
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ) {
        notify(downloadId, buildProgress(downloadId, title, text, progress, indeterminate))
    }

    fun cancelNotification(notificationId: Int) = manager.cancel(notificationId)

    fun showComplete(resultId: Int, title: String, videoUri: Uri, mimeType: String) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(videoUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val share = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, videoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            context.getString(R.string.share_video)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val builder = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notif_saved_title))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setColor(ContextCompat.getColor(context, R.color.colorAccent))
            .setAutoCancel(true)
            .setContentIntent(activityIntent(resultId * 4, view))
            .addAction(0, context.getString(R.string.play), activityIntent(resultId * 4 + 1, view))
            .addAction(0, context.getString(R.string.share), activityIntent(resultId * 4 + 2, share))
        notify(resultId, builder)
    }

    fun showError(resultId: Int, message: String, sourceUrl: String?) {
        val builder = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notif_failed_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setColor(ContextCompat.getColor(context, R.color.error))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(resultId * 4))
        if (sourceUrl != null) {
            val retry = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_RETRY
                putExtra(DownloadService.EXTRA_URL, sourceUrl)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            builder.addAction(0, context.getString(R.string.retry), activityIntent(resultId * 4 + 3, retry))
        }
        notify(resultId, builder)
    }

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun openAppIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return activityIntent(requestCode, intent)
    }

    private fun activityIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelIntent(downloadId: Int): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getService(
            context,
            downloadId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
