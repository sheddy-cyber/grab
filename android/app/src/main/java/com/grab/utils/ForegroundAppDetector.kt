package com.grab.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Detects which app was in the foreground before grab briefly takes focus,
 * then restores that app after the shortcut finishes.
 */
object ForegroundAppDetector {

    private const val TAG = "ForegroundAppDetector"

    private val IGNORED_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.googlequicksearchbox",
        "com.sec.android.app.launcher",
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.sidegesturepad",
        "com.samsung.android.app.taskedge",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.oneplus.launcher",
        "com.grab",
    )

    private val IGNORED_PREFIXES = listOf(
        "com.android.launcher",
        "com.samsung.android.app.cocktail",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
    )

    fun detectPreviousApp(context: Context): String? {
        detectFromRunningProcesses(context)?.let { return it }
        detectFromUsageEvents(context)?.let { return it }
        return null
    }

    fun restorePreviousApp(context: Context, packageName: String?) {
        if (packageName.isNullOrBlank() || shouldIgnore(packageName)) return

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            context.startActivity(launchIntent)
            if (context is Activity) {
                context.overridePendingTransition(0, 0)
            }
            Log.d(TAG, "Restored previous app: $packageName")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore $packageName", e)
        }
    }

    private fun detectFromRunningProcesses(context: Context): String? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val myPackage = context.packageName

        val candidate = activityManager.runningAppProcesses
            ?.asSequence()
            ?.map { process ->
                process.processName.substringBefore(":") to process.importance
            }
            ?.filter { (packageName, importance) ->
                packageName != myPackage &&
                    !shouldIgnore(packageName) &&
                    importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            }
            ?.minByOrNull { it.second }
            ?.first

        if (candidate != null) {
            Log.d(TAG, "Detected previous app from running processes: $candidate")
        }
        return candidate
    }

    private fun detectFromUsageEvents(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        if (!hasUsageAccess(context)) return null

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 5000
        val events = usageStatsManager.queryEvents(start, end)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        val myPackage = context.packageName

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (!isForegroundEvent) continue

            val packageName = event.packageName
            if (packageName != myPackage && !shouldIgnore(packageName)) {
                lastPackage = packageName
            }
        }

        if (lastPackage != null) {
            Log.d(TAG, "Detected previous app from usage events: $lastPackage")
        }
        return lastPackage
    }

    private fun hasUsageAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            end - 60_000,
            end
        )
        return !stats.isNullOrEmpty()
    }

    private fun shouldIgnore(packageName: String): Boolean {
        if (packageName in IGNORED_PACKAGES) return true
        return IGNORED_PREFIXES.any { packageName.startsWith(it) }
    }
}
