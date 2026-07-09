package com.grabam.utils

import java.net.URI

object UrlUtils {
    enum class Platform(val displayName: String) {
        TWITTER_X("Twitter/X"),
        YOUTUBE("YouTube"),
        FACEBOOK("Facebook"),
        INSTAGRAM("Instagram")
    }

    data class SupportedVideoUrl(
        val url: String,
        val platform: Platform
    )

    private val supportedUrlPattern = Regex(
        pattern = """\b(?:https?://)?(?:[a-z0-9-]+\.)*(?:twitter\.com|x\.com|youtube\.com|youtube-nocookie\.com|youtu\.be|facebook\.com|fb\.watch|fb\.com|instagram\.com)/[^\s<>"']+""",
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

        return normalizeTwitterUrl(uri) ?: normalizeYouTubeUrl(uri) ?: normalizeFacebookUrl(uri) ?: normalizeInstagramUrl(uri)
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

    private fun normalizeFacebookUrl(uri: URI): SupportedVideoUrl? {
        val host = uri.host?.lowercase() ?: return null
        if (!isFacebookHost(host)) return null

        val segments = uri.pathSegments()
        if (segments.isEmpty()) return null
        val lowerSegments = segments.map { it.lowercase() }

        // fb.watch/<shortcode> short links
        if (host == "fb.watch" || host.endsWith(".fb.watch")) {
            val shortcode = segments.firstOrNull() ?: return null
            return SupportedVideoUrl(
                url = "https://fb.watch/$shortcode",
                platform = Platform.FACEBOOK
            )
        }

        // facebook.com/watch?v=<id>
        if (lowerSegments.first() == "watch") {
            val videoId = uri.queryParameter("v") ?: return null
            return SupportedVideoUrl(
                url = "https://www.facebook.com/watch?v=$videoId",
                platform = Platform.FACEBOOK
            )
        }

        // facebook.com/<page>/videos/<id>/
        val videosIndex = lowerSegments.indexOf("videos")
        if (videosIndex != -1) {
            val videoId = segments.getOrNull(videosIndex + 1) ?: return null
            return SupportedVideoUrl(
                url = "https://www.facebook.com/watch?v=$videoId",
                platform = Platform.FACEBOOK
            )
        }

        // facebook.com/reel/<id> or /reels/<id>
        val reelIndex = lowerSegments.indexOfFirst { it == "reel" || it == "reels" }
        if (reelIndex != -1) {
            val reelId = segments.getOrNull(reelIndex + 1) ?: return null
            return SupportedVideoUrl(
                url = "https://www.facebook.com/reel/$reelId",
                platform = Platform.FACEBOOK
            )
        }

        // facebook.com/share/v/<id>/ or /share/r/<id>/ (share links, kept as-is)
        if (lowerSegments.first() == "share" && segments.size >= 3) {
            return SupportedVideoUrl(
                url = "https://www.facebook.com/${segments.joinToString("/")}",
                platform = Platform.FACEBOOK
            )
        }

        // m.facebook.com/video.php?v=<id> and /photo.php?v=<id> (mobile web)
        if (lowerSegments.first() in listOf("video.php", "photo.php")) {
            val videoId = uri.queryParameter("v") ?: return null
            return SupportedVideoUrl(
                url = "https://www.facebook.com/watch?v=$videoId",
                platform = Platform.FACEBOOK
            )
        }

        return null
    }

    private fun normalizeInstagramUrl(uri: URI): SupportedVideoUrl? {
        val host = uri.host?.lowercase() ?: return null
        if (!isInstagramHost(host)) return null

        val segments = uri.pathSegments()
        val lowerSegments = segments.map { it.lowercase() }

        // Supports /reel/<code>, /p/<code>, /tv/<code>, and the
        // username-prefixed variant /<username>/reel/<code>
        val typeIndex = lowerSegments.indexOfFirst { it == "reel" || it == "reels" || it == "p" || it == "tv" }
        if (typeIndex == -1) return null

        val shortcode = segments.getOrNull(typeIndex + 1) ?: return null
        val typeSegment = if (lowerSegments[typeIndex] == "reels") "reel" else lowerSegments[typeIndex]

        return SupportedVideoUrl(
            url = "https://www.instagram.com/$typeSegment/$shortcode/",
            platform = Platform.INSTAGRAM
        )
    }

    private fun isFacebookHost(host: String): Boolean {
        return host == "facebook.com" ||
            host.endsWith(".facebook.com") ||
            host == "fb.watch" ||
            host.endsWith(".fb.watch") ||
            host == "fb.com" ||
            host.endsWith(".fb.com")
    }

    private fun isInstagramHost(host: String): Boolean {
        return host == "instagram.com" || host.endsWith(".instagram.com")
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
