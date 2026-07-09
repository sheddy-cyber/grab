package com.grabam.utils

import kotlin.text.Charsets

/**
 * Detects container format from the first bytes of a media stream.
 * Used to reject HTML/JSON error pages and to fix mismatched extensions.
 */
object MediaFormatUtils {

    data class DetectedFormat(
        val extension: String,
        val mimeType: String
    )

    fun detectFromHeader(header: ByteArray): DetectedFormat? {
        if (header.size < 4) return null

        // MP4: [size]ftyp
        if (header.size >= 8 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        ) {
            return DetectedFormat("mp4", "video/mp4")
        }

        // WebM/MKV (EBML: 1A 45 DF A3)
        if (header[0] == 0x1A.toByte() &&
            header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() &&
            header[3] == 0xA3.toByte()
        ) {
            return DetectedFormat("webm", "video/webm")
        }

        // QuickTime (.mov: [size]moov)
        if (header.size >= 8 &&
            header[4] == 'm'.code.toByte() &&
            header[5] == 'o'.code.toByte() &&
            header[6] == 'o'.code.toByte() &&
            header[7] == 'v'.code.toByte()
        ) {
            return DetectedFormat("mov", "video/quicktime")
        }

        // AVI: RIFF....AVI
        if (header.size >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'A'.code.toByte() &&
            header[9] == 'V'.code.toByte() &&
            header[10] == 'I'.code.toByte()
        ) {
            return DetectedFormat("avi", "video/x-msvideo")
        }

        // MPEG-TS (Sync byte 0x47 every 188 bytes)
        if (header.size >= 188 * 2 &&
            header[0] == 0x47.toByte() &&
            header[188] == 0x47.toByte()
        ) {
            return DetectedFormat("ts", "video/mp2t")
        }

        // FLV
        if (header.size >= 4 &&
            header[0] == 'F'.code.toByte() &&
            header[1] == 'L'.code.toByte() &&
            header[2] == 'V'.code.toByte() &&
            header[3] == 0x01.toByte()
        ) {
            return DetectedFormat("flv", "video/x-flv")
        }

        // 3GP
        if (header.size >= 8 &&
            header[4] == '3'.code.toByte() &&
            header[5] == 'g'.code.toByte() &&
            header[6] == 'p'.code.toByte()
        ) {
            return DetectedFormat("3gp", "video/3gpp")
        }

        // MPEG-PS (Program Stream): 00 00 01 BA or 00 00 01 BC
        if (header.size >= 4 &&
            header[0] == 0x00.toByte() &&
            header[1] == 0x00.toByte() &&
            header[2] == 0x01.toByte() &&
            (header[3] == 0xBA.toByte() || header[3] == 0xBC.toByte())
        ) {
            return DetectedFormat("mpg", "video/mpeg")
        }

        return null
    }

    fun isLikelyErrorPayload(header: ByteArray): Boolean {
        if (header.isEmpty()) return true

        // Safely decode as UTF-8 string to check for common error page markers
        val prefix = String(header.take(512).toByteArray(), Charsets.UTF_8).trimStart()

        if (prefix.startsWith("<!DOCTYPE", ignoreCase = true) ||
            prefix.startsWith("<html", ignoreCase = true) ||
            prefix.startsWith("<?xml", ignoreCase = true) ||
            prefix.startsWith("<body", ignoreCase = true)
        ) {
            return true
        }

        if (prefix.startsWith("{") || prefix.startsWith("[")) {
            // Check if it's actually JSON by looking for typical error keys
            val lower = prefix.lowercase()
            if (lower.contains("\"error\"") || lower.contains("\"message\"") || lower.contains("\"detail\"")) {
                return true
            }
        }

        // HLS playlist - this is a playlist, not a video file
        // We reject it because saving a playlist as an MP4 causes an "unsupported media error" in the gallery
        if (prefix.startsWith("#EXTM3U", ignoreCase = true)) {
            return true
        }

        return false
    }

    fun mimeTypeForExtension(extension: String): String {
        return when (extension.lowercase().trim('.')) {
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"
            "flv" -> "video/x-flv"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "m4v" -> "video/x-m4v"
            "mpg", "mpeg" -> "video/mpeg"
            else -> "video/mp4"
        }
    }


}
