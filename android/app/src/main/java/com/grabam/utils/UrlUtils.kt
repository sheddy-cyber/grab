package com.grabam.utils

import java.net.URI

object UrlUtils {
    enum class Platform(val displayName: String) {
        TWITTER_X("Twitter/X"),
        YOUTUBE("YouTube")
    }

    data class SupportedVideoUrl(
        val url: String,
        val platform: Platform
    )

    private val supportedUrlPattern = Regex(
        pattern = """\b(?:https?://)?(?:[a-z0-9-]+\.)*(?:twitter\.com|x\.com|youtube\.com|youtube-nocookie\.com|youtu\.be)/[^\s<>"']+""",
        option = RegexOption.IGNORE_CASE
    )
    private val twitterStatusIdPattern = Regex("""^\d+$""")
    private val youtubeVideoIdPattern = Regex("""^[A-Za-z0-9_-]{6,128}$""")

    fun extractSupportedUrl(text: String): SupportedVideoUrl? {
        supportedUrlPattern.findAll(text)
            .mapNotNull { normalizeCandidate(it.value) }
            .firstOrNull()
            ?.let { return it }

        return normalizeCandidate(text)
    }

    private fun normalizeCandidate(rawCandidate: String): SupportedVideoUrl? {
        val candidate = prepareCandidate(rawCandidate) ?: return null
        val uri = try {
            URI(candidate)
        } catch (e: Exception) {
            return null
        }

        return normalizeTwitterUrl(uri) ?: normalizeYouTubeUrl(uri)
    }

    private fun prepareCandidate(rawCandidate: String): String? {
        var candidate = rawCandidate.trim()
        if (candidate.isBlank()) return null

        candidate = candidate.trimEnd('.', ',', ';', '!', '?', ')', ']', '}', '"', '\'')
        if (!candidate.startsWith("http://", ignoreCase = true) &&
            !candidate.startsWith("https://", ignoreCase = true)
        ) {
            candidate = "https://$candidate"
        }

        return candidate
    }

    private fun normalizeTwitterUrl(uri: URI): SupportedVideoUrl? {
        val host = uri.host?.lowercase() ?: return null
        if (!isTwitterHost(host)) return null

        val segments = uri.pathSegments()
        val statusIndex = segments.indexOfFirst { it.equals("status", ignoreCase = true) }
        val statusId = segments.getOrNull(statusIndex + 1)
            ?.takeIf { twitterStatusIdPattern.matches(it) }
            ?: return null

        return SupportedVideoUrl(
            url = "https://twitter.com/i/status/$statusId",
            platform = Platform.TWITTER_X
        )
    }

    private fun normalizeYouTubeUrl(uri: URI): SupportedVideoUrl? {
        val host = uri.host?.lowercase() ?: return null
        if (!isYouTubeHost(host)) return null

        val segments = uri.pathSegments()
        val firstSegment = segments.firstOrNull()?.lowercase()
        val isShortsUrl = firstSegment == "shorts"

        val videoId = when {
            host == "youtu.be" || host.endsWith(".youtu.be") -> segments.firstOrNull()
            firstSegment == "watch" -> uri.queryParameter("v")
            isShortsUrl -> segments.getOrNull(1)
            firstSegment == "embed" || firstSegment == "v" || firstSegment == "live" -> {
                segments.getOrNull(1)
            }
            else -> null
        }?.takeIf { youtubeVideoIdPattern.matches(it) } ?: return null

        val normalizedUrl = if (isShortsUrl) {
            "https://www.youtube.com/shorts/$videoId"
        } else {
            "https://www.youtube.com/watch?v=$videoId"
        }

        return SupportedVideoUrl(
            url = normalizedUrl,
            platform = Platform.YOUTUBE
        )
    }

    private fun isTwitterHost(host: String): Boolean {
        return host == "twitter.com" ||
            host.endsWith(".twitter.com") ||
            host == "x.com" ||
            host.endsWith(".x.com")
    }

    private fun isYouTubeHost(host: String): Boolean {
        return host == "youtu.be" ||
            host.endsWith(".youtu.be") ||
            host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com")
    }

    private fun URI.pathSegments(): List<String> {
        return rawPath
            ?.trim('/')
            ?.split('/')
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private fun URI.queryParameter(name: String): String? {
        return rawQuery
            ?.replace("&amp;", "&")
            ?.split("&")
            ?.firstNotNullOfOrNull { part ->
                val key = part.substringBefore("=")
                val value = part.substringAfter("=", missingDelimiterValue = "")
                value.takeIf { key == name && it.isNotBlank() }
            }
    }
}
