package com.grab.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMessagesTest {

    @Test
    fun grabExceptionMessageIsShownAsIs() {
        assertEquals("Custom", ErrorMessages.from(GrabException("Custom")))
    }

    @Test
    fun offlineWinsOverNetworkErrors() {
        assertEquals(ErrorMessages.NO_INTERNET, ErrorMessages.from(UnknownHostException("x"), hasInternet = false))
    }

    @Test
    fun timeoutsAndServerStartupAreExplained() {
        assertTrue(ErrorMessages.from(SocketTimeoutException()).contains("too long"))
        assertTrue(ErrorMessages.from(ServerUnavailableException(503)).contains("starting up"))
    }

    @Test
    fun backendLoginErrorsMapToLoginMessage() {
        val raw = "Instagram requires login. Please provide a cookies.txt file with your session cookies"
        assertTrue(ErrorMessages.fromBackend(raw, 500).contains("needs a login"))
    }

    @Test
    fun youtubeBotDetectionTakesPriorityOverCookiesHint() {
        val raw = "YouTube bot detection blocked the request. Please upload a cookies.txt file"
        assertTrue(ErrorMessages.fromBackend(raw, 500).contains("temporarily blocking"))
    }

    @Test
    fun noFormatAndUnsupportedErrorsAreMapped() {
        assertTrue(ErrorMessages.fromBackend("No playable video format found", 500).contains("No downloadable video"))
        assertTrue(ErrorMessages.fromBackend("Unsupported URL. Use a Twitter/X...", 400).contains("isn't supported"))
        assertTrue(ErrorMessages.fromBackend(null, 429).contains("Too many requests"))
    }

    @Test
    fun downloadHttpCodes() {
        assertTrue(ErrorMessages.forDownloadHttp(403).contains("Access denied"))
        assertTrue(ErrorMessages.forDownloadHttp(404).contains("not found"))
        assertTrue(ErrorMessages.forDownloadHttp(418).contains("418"))
    }

    @Test
    fun fileNamesKeepUnicodeAndDropIllegalCharacters() {
        assertEquals("日本語 の 動画", FileNames.sanitize("日本語/の:動画"))
        assertEquals("a b", FileNames.sanitize("  a\n\tb  "))
        assertEquals("video", FileNames.sanitize("///"))
        assertEquals("video", FileNames.sanitize(null))
        assertEquals(80, FileNames.sanitize("x".repeat(200)).length)
    }

    @Test
    fun backendUrlNormalization() {
        assertEquals("https://a.b", BackendConfig.normalize(" https://a.b/extract?url= "))
        assertEquals("http://10.0.2.2:8000", BackendConfig.normalize("http://10.0.2.2:8000/"))
        assertTrue(BackendConfig.isValid("https://grab.example.com"))
        assertFalse(BackendConfig.isValid("grab.example.com"))
        assertFalse(BackendConfig.isValid("https://"))
    }
}
