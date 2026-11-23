package com.grab.utils

import android.content.Context

/** Reads and normalizes the user-configurable backend base URL. */
object BackendConfig {
    const val DEFAULT_BACKEND_URL = "https://grab-am.onrender.com"
    private const val PREFS = "grab_am_settings"
    private const val KEY_URL = "backend_url"

    fun rawUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_BACKEND_URL)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_BACKEND_URL

    fun save(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, url.trim()).apply()
    }

    fun baseUrl(context: Context): String = normalize(rawUrl(context))

    /** Strips any `/extract...` suffix and trailing slashes users may have pasted in. */
    fun normalize(url: String): String {
        var base = url.trim()
        if (base.contains("/extract")) base = base.substringBefore("/extract")
        return base.trimEnd('/')
    }

    fun isValid(url: String): Boolean {
        val base = normalize(url)
        return (base.startsWith("http://", true) || base.startsWith("https://", true)) &&
            base.substringAfter("://").isNotBlank()
    }

    fun extractUrl(context: Context, encodedVideoUrl: String) =
        "${baseUrl(context)}/extract?url=$encodedVideoUrl"

    fun healthUrl(base: String) = "${normalize(base)}/health"
}
