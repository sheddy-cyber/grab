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
    fun extractsFacebookWatchUrl() {
        val result = UrlUtils.extractSupportedUrl(
            "Check this out https://www.facebook.com/watch?v=1234567890123456&extra=1"
        )

        assertEquals(UrlUtils.Platform.FACEBOOK, result?.platform)
        assertEquals("https://www.facebook.com/watch?v=1234567890123456", result?.url)
    }

    @Test
    fun extractsFacebookVideosUrl() {
        val result = UrlUtils.extractSupportedUrl(
            "https://www.facebook.com/SomePage/videos/9876543210/"
        )

        assertEquals(UrlUtils.Platform.FACEBOOK, result?.platform)
        assertEquals("https://www.facebook.com/watch?v=9876543210", result?.url)
    }

    @Test
    fun extractsFacebookReelUrl() {
        val result = UrlUtils.extractSupportedUrl(
            "https://www.facebook.com/reel/1122334455"
        )

        assertEquals(UrlUtils.Platform.FACEBOOK, result?.platform)
        assertEquals("https://www.facebook.com/reel/1122334455", result?.url)
    }

    @Test
    fun extractsFbWatchShortLink() {
        val result = UrlUtils.extractSupportedUrl("https://fb.watch/abcDEF123/")

        assertEquals(UrlUtils.Platform.FACEBOOK, result?.platform)
        assertEquals("https://fb.watch/abcDEF123", result?.url)
    }

    @Test
    fun extractsInstagramReelUrl() {
        val result = UrlUtils.extractSupportedUrl(
            "https://www.instagram.com/reel/CxYz123Ab_-/?igsh=token"
        )

        assertEquals(UrlUtils.Platform.INSTAGRAM, result?.platform)
        assertEquals("https://www.instagram.com/reel/CxYz123Ab_-/", result?.url)
    }

    @Test
    fun extractsInstagramReelUrlWithUsernamePrefix() {
        val result = UrlUtils.extractSupportedUrl(
            "https://instagram.com/someuser/reel/CxYz123Ab_-/"
        )

        assertEquals(UrlUtils.Platform.INSTAGRAM, result?.platform)
        assertEquals("https://www.instagram.com/reel/CxYz123Ab_-/", result?.url)
    }

    @Test
    fun extractsInstagramPostUrl() {
        val result = UrlUtils.extractSupportedUrl("https://www.instagram.com/p/CxYz123Ab_-/")

        assertEquals(UrlUtils.Platform.INSTAGRAM, result?.platform)
        assertEquals("https://www.instagram.com/p/CxYz123Ab_-/", result?.url)
    }

    @Test
    fun rejectsUnsupportedUrls() {
        assertNull(UrlUtils.extractSupportedUrl("https://example.com/video/123"))
    }
}
