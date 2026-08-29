package com.grab.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.grab.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "grab_downloads"
        const val CHANNEL_NAME = "grab Downloads"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of video downloads"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getBaseNotification(title: String, content: String, downloadId: Int): NotificationCompat.Builder {
        val customView = android.widget.RemoteViews(context.packageName, R.layout.notification_download)
        customView.setTextViewText(R.id.tvTitle, title)
        customView.setTextViewText(R.id.tvContent, content)
        customView.setProgressBar(R.id.progressBar, 100, 0, true)
        
        val cancelIntent = getActionIntent(com.grab.service.DownloadService.ACTION_CANCEL_DOWNLOAD, downloadId)
        customView.setOnClickPendingIntent(R.id.btnCancel, cancelIntent)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title) // kept in extras for retrieval
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setStyle(androidx.core.app.NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup("GRAB_DOWNLOADS")
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun getActionIntent(action: String, downloadId: Int): android.app.PendingIntent {
        val intent = android.content.Intent(context, com.grab.service.DownloadService::class.java).apply {
            this.action = action
            putExtra(com.grab.service.DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        return android.app.PendingIntent.getService(
            context,
            downloadId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun updateProgress(
        builder: NotificationCompat.Builder,
        progress: Int,
        content: String? = null,
        indeterminate: Boolean = false,
        notificationId: Int = NOTIFICATION_ID
    ) {
        val title = builder.build().extras.getString(NotificationCompat.EXTRA_TITLE) ?: "grab"
        
        val customView = android.widget.RemoteViews(context.packageName, R.layout.notification_download)
        customView.setTextViewText(R.id.tvTitle, title)
        if (content != null) {
            customView.setTextViewText(R.id.tvContent, content)
            builder.setContentText(content) // keep extra updated
        }
        customView.setProgressBar(R.id.progressBar, 100, progress.coerceIn(0, 100), indeterminate)
        
        val cancelIntent = getActionIntent(com.grab.service.DownloadService.ACTION_CANCEL_DOWNLOAD, notificationId)
        customView.setOnClickPendingIntent(R.id.btnCancel, cancelIntent)
        
        builder.setCustomContentView(customView)
        builder.setCustomBigContentView(customView)
        
        if (hasNotificationPermission()) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun cancelNotification(notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    fun showComplete(title: String, videoUri: android.net.Uri? = null, mimeType: String = "video/mp4", notificationId: Int = NOTIFICATION_ID) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val contentIntent = videoUri?.let {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(it, mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setLargeIcon(appLogo())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
        
        notificationManager.cancel(notificationId)
        if (hasNotificationPermission()) {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    fun showError(message: String, notificationId: Int = NOTIFICATION_ID) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setLargeIcon(appLogo())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        notificationManager.cancel(notificationId)
        if (hasNotificationPermission()) {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    fun showMessage(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_download)
            .setLargeIcon(appLogo())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (hasNotificationPermission()) {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    private fun appLogo() =
        BitmapFactory.decodeResource(context.resources, R.drawable.app_logo)
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.ic_download)
}
