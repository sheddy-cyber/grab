package com.grabam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFormatUtilsTest {

    @Test
    fun detectFromHeader_recognizesMp4() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte()
        )

        val detected = MediaFormatUtils.detectFromHeader(header)
        assertNotNull(detected)
        assertEquals("mp4", detected?.extension)
        assertEquals("video/mp4", detected?.mimeType)
    }

    @Test
    fun detectFromHeader_recognizesWebm() {
        val header = byteArrayOf(
            0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(),
            0x01, 0x00, 0x00, 0x00
        )

        val detected = MediaFormatUtils.detectFromHeader(header)
        assertNotNull(detected)
        assertEquals("webm", detected?.extension)
    }

    @Test
    fun isLikelyErrorPayload_detectsHtmlAndJson() {
        assertTrue(MediaFormatUtils.isLikelyErrorPayload("<html><body>error</body></html>".toByteArray()))
        assertTrue(MediaFormatUtils.isLikelyErrorPayload("{\"error\":\"blocked\"}".toByteArray()))
        assertTrue(MediaFormatUtils.isLikelyErrorPayload("#EXTM3U\n".toByteArray()))
        assertFalse(MediaFormatUtils.isLikelyErrorPayload(byteArrayOf(0x00, 0x00, 0x00, 0x20)))
    }

    @Test
    fun detectFromHeader_rejectsUnknownPayload() {
        assertNull(MediaFormatUtils.detectFromHeader("plain text".toByteArray()))
    }
}
