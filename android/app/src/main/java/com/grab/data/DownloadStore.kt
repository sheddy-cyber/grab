package com.grab.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class DownloadStatus { ACTIVE, COMPLETED, FAILED }

data class DownloadRecord(
    val id: String,
    val sourceUrl: String,
    val platform: String,
    val createdAt: Long,
    val status: DownloadStatus = DownloadStatus.ACTIVE,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    /** 0..100, or -1 when the size is unknown. */
    val progress: Int = -1,
    val statusText: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val fileUri: String? = null,
    val mimeType: String? = null,
    val error: String? = null
) {
    val uri: Uri? get() = fileUri?.let(Uri::parse)
}

/**
 * Single source of truth for download state. The service writes to it; the UI observes it.
 * Finished records are persisted so the "Recent" list survives restarts.
 */
object DownloadStore {
    private const val PREFS = "grab_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 40

    private lateinit var prefs: SharedPreferences
    private val _records = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val records: StateFlow<List<DownloadRecord>> = _records.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Anything still "active" from a previous process was interrupted (app killed, reboot...).
        val loaded = load().map {
            if (it.status == DownloadStatus.ACTIVE) {
                it.copy(status = DownloadStatus.FAILED, error = "Download was interrupted", progress = -1)
            } else it
        }
        _records.value = loaded
        persist()
    }

    fun get(id: String): DownloadRecord? = _records.value.firstOrNull { it.id == id }

    fun findActive(sourceUrl: String): DownloadRecord? =
        _records.value.firstOrNull { it.status == DownloadStatus.ACTIVE && it.sourceUrl == sourceUrl }

    fun create(sourceUrl: String, platform: String): DownloadRecord {
        val record = DownloadRecord(
            id = UUID.randomUUID().toString(),
            sourceUrl = sourceUrl,
            platform = platform,
            createdAt = System.currentTimeMillis()
        )
        // A new attempt replaces earlier failed attempts of the same link.
        _records.update { list ->
            (listOf(record) + list.filterNot { it.status == DownloadStatus.FAILED && it.sourceUrl == sourceUrl })
                .take(MAX_ITEMS)
        }
        persist()
        return record
    }

    /** In-memory update for frequent progress ticks (not persisted). */
    fun updateProgress(id: String, transform: (DownloadRecord) -> DownloadRecord) {
        _records.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    /** Update that should survive a restart (metadata, completion, failure). */
    fun update(id: String, transform: (DownloadRecord) -> DownloadRecord) {
        updateProgress(id, transform)
        persist()
    }

    fun remove(id: String) {
        _records.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clearFinished() {
        _records.update { list -> list.filter { it.status == DownloadStatus.ACTIVE } }
        persist()
    }

    private fun persist() {
        if (!::prefs.isInitialized) return
        val array = JSONArray()
        _records.value.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun load(): List<DownloadRecord> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toRecord() }
        }.getOrDefault(emptyList())
    }

    private fun DownloadRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("sourceUrl", sourceUrl)
        put("platform", platform)
        put("createdAt", createdAt)
        put("status", status.name)
        putOpt("title", title)
        putOpt("thumbnailUrl", thumbnailUrl)
        put("totalBytes", totalBytes)
        putOpt("fileUri", fileUri)
        putOpt("mimeType", mimeType)
        putOpt("error", error)
    }

    private fun JSONObject.toRecord(): DownloadRecord? {
        val id = optString("id").ifBlank { return null }
        return DownloadRecord(
            id = id,
            sourceUrl = optString("sourceUrl"),
            platform = optString("platform"),
            createdAt = optLong("createdAt"),
            status = runCatching { DownloadStatus.valueOf(optString("status")) }.getOrDefault(DownloadStatus.FAILED),
            title = optStringOrNull("title"),
            thumbnailUrl = optStringOrNull("thumbnailUrl"),
            totalBytes = optLong("totalBytes", -1L),
            fileUri = optStringOrNull("fileUri"),
            mimeType = optStringOrNull("mimeType"),
            error = optStringOrNull("error")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
