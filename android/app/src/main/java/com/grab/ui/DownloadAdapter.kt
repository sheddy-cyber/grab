package com.grab.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.grab.R
import com.grab.data.DownloadRecord
import com.grab.data.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DownloadAdapter(
    private val scope: CoroutineScope,
    private val thumbnails: ThumbnailLoader,
    private val listener: Listener
) : ListAdapter<DownloadRecord, DownloadAdapter.Holder>(Diff) {

    interface Listener {
        fun onOpen(record: DownloadRecord)
        fun onRetry(record: DownloadRecord)
        fun onCancel(record: DownloadRecord)
        fun onMore(record: DownloadRecord, anchor: View)
    }

    private object Diff : DiffUtil.ItemCallback<DownloadRecord>() {
        override fun areItemsTheSame(old: DownloadRecord, new: DownloadRecord) = old.id == new.id
        override fun areContentsTheSame(old: DownloadRecord, new: DownloadRecord) = old == new
        // Progress-only changes are rebound in place, without the change animation or a thumbnail reload.
        override fun getChangePayload(old: DownloadRecord, new: DownloadRecord): Any? =
            if (old.status == new.status && old.title == new.title && old.thumbnailUrl == new.thumbnailUrl &&
                old.fileUri == new.fileUri
            ) PAYLOAD_PROGRESS else null
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), full = true)

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        holder.bind(getItem(position), full = payloads.isEmpty())
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumb: ImageView = view.findViewById(R.id.ivThumb)
        private val badge: ImageView = view.findViewById(R.id.ivBadge)
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val meta: TextView = view.findViewById(R.id.tvMeta)
        private val progress: LinearProgressIndicator = view.findViewById(R.id.progress)
        private val action: MaterialButton = view.findViewById(R.id.btnAction)
        private var thumbJob: Job? = null
        private var thumbKey: String? = null
        private var boundId: String? = null

        fun bind(record: DownloadRecord, full: Boolean) {
            val ctx = itemView.context
            meta.text = buildMeta(record)
            bindProgress(record)
            if (!full) return

            title.text = record.title ?: ctx.getString(R.string.platform_video, record.platform)
            bindThumbnail(record)

            when (record.status) {
                DownloadStatus.ACTIVE -> {
                    action.setIconResource(R.drawable.ic_close)
                    action.contentDescription = ctx.getString(R.string.cancel)
                    action.setOnClickListener { listener.onCancel(record) }
                    itemView.setOnClickListener(null)
                    itemView.isClickable = false
                }
                DownloadStatus.COMPLETED -> {
                    action.setIconResource(R.drawable.ic_more)
                    action.contentDescription = ctx.getString(R.string.more_options)
                    action.setOnClickListener { listener.onMore(record, it) }
                    itemView.setOnClickListener { listener.onOpen(record) }
                }
                DownloadStatus.FAILED -> {
                    action.setIconResource(R.drawable.ic_refresh)
                    action.contentDescription = ctx.getString(R.string.retry)
                    action.setOnClickListener { listener.onRetry(record) }
                    itemView.setOnClickListener { listener.onRetry(record) }
                }
            }
            itemView.setOnLongClickListener { listener.onMore(record, action); true }
        }

        private fun bindProgress(record: DownloadRecord) {
            val active = record.status == DownloadStatus.ACTIVE
            progress.isVisible = active
            if (!active) return
            val indeterminate = record.progress < 0
            if (progress.isIndeterminate != indeterminate) {
                // Switching modes requires the indicator to be hidden first.
                progress.visibility = View.INVISIBLE
                progress.isIndeterminate = indeterminate
                progress.visibility = View.VISIBLE
            }
            if (!indeterminate) progress.setProgressCompat(record.progress, true)
        }

        private fun bindThumbnail(record: DownloadRecord) {
            val key = thumbnails.keyFor(record)
            val badgeRes = when (record.status) {
                DownloadStatus.ACTIVE -> R.drawable.ic_download
                DownloadStatus.COMPLETED -> R.drawable.ic_play
                DownloadStatus.FAILED -> R.drawable.ic_error
            }
            badge.setImageResource(badgeRes)
            badge.setColorFilter(
                ContextCompat.getColor(
                    itemView.context,
                    if (record.status == DownloadStatus.FAILED) R.color.error else R.color.textSecondary
                )
            )

            if (key == thumbKey && thumb.drawable != null) {
                badge.isVisible = record.status == DownloadStatus.FAILED
                return
            }
            thumbJob?.cancel()
            thumbKey = key
            if (boundId != record.id) thumb.setImageDrawable(null)
            boundId = record.id
            val cached = key?.let(thumbnails::cached)
            // Keep the previous image (e.g. the web thumbnail) while the saved file's frame loads.
            if (cached != null || key == null) thumb.setImageBitmap(cached)
            badge.isVisible = thumb.drawable == null || record.status == DownloadStatus.FAILED
            if (cached != null || key == null) return

            thumbJob = scope.launch {
                val bitmap = thumbnails.load(record) ?: return@launch
                if (thumbKey == key) {
                    thumb.setImageBitmap(bitmap)
                    thumb.alpha = 0f
                    thumb.animate().alpha(1f).setDuration(180).start()
                    badge.isVisible = record.status == DownloadStatus.FAILED
                }
            }
        }

        private fun buildMeta(record: DownloadRecord): CharSequence {
            val ctx = itemView.context
            val sb = SpannableStringBuilder()
            sb.append(record.platform, ForegroundColorSpan(platformColor(record.platform)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("  ·  ")
            when (record.status) {
                DownloadStatus.ACTIVE -> sb.append(record.statusText ?: ctx.getString(R.string.status_queued))
                DownloadStatus.COMPLETED -> {
                    if (record.totalBytes > 0) {
                        sb.append(Formatter.formatShortFileSize(ctx, record.totalBytes)).append("  ·  ")
                    }
                    val now = System.currentTimeMillis()
                    sb.append(
                        if (now - record.createdAt < DateUtils.MINUTE_IN_MILLIS) ctx.getString(R.string.just_now)
                        else DateUtils.getRelativeTimeSpanString(
                            record.createdAt, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
                        )
                    )
                }
                DownloadStatus.FAILED -> sb.append(
                    record.error ?: ctx.getString(R.string.notif_failed_title),
                    ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.error)),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return sb
        }

        private fun platformColor(platform: String): Int = ContextCompat.getColor(
            itemView.context,
            when {
                platform.contains("YouTube", true) -> R.color.brandYouTube
                platform.contains("Instagram", true) -> R.color.brandInstagramEnd
                platform.contains("Facebook", true) -> R.color.brandFacebook
                platform.contains("Twitter", true) || platform == "X" -> R.color.brandTwitter
                else -> R.color.textSecondary
            }
        )
    }

    companion object {
        private const val PAYLOAD_PROGRESS = "progress"
    }
}
