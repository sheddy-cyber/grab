package com.grabam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun extractsTwitterStatusFromSharedText() {
        val result = UrlUtils.extractSupportedUrl(
            "Watch this https://x.com/example/status/1801234567890123456/video/1?s=20"
        )

        assertEquals(UrlUtils.Platform.TWITTER_X, result?.platform)
        assertEquals("https://twitter.com/i/status/1801234567890123456", result?.url)
    }

    @Test
    fun extractsYouTubeWatchUrl() {
        val result = UrlUtils.extractSupportedUrl(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&ab_channel=Official"
        )

        assertEquals(UrlUtils.Platform.YOUTUBE, result?.platform)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", result?.url)
    }

    @Test
    fun extractsYouTubeShortsUrl() {
        val result = UrlUtils.extractSupportedUrl(
            "https://youtube.com/shorts/abcD_123-xy?si=shareToken"
        )

        assertEquals(UrlUtils.Platform.YOUTUBE, result?.platform)
        assertEquals("https://www.youtube.com/shorts/abcD_123-xy", result?.url)
    }

    @Test
    fun extractsShortYoutuBeUrlWithoutScheme() {
        val result = UrlUtils.extractSupportedUrl("youtu.be/dQw4w9WgXcQ?si=abc")

        assertEquals(UrlUtils.Platform.YOUTUBE, result?.platform)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", result?.url)
    }

    @Test
    fun rejectsUnsupportedUrls() {
        assertNull(UrlUtils.extractSupportedUrl("https://example.com/video/123"))
    }
}
