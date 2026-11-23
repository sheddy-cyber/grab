package com.grab.utils

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** An error whose message is already safe and friendly enough to show to the user. */
class GrabException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

/** Retryable backend condition (cold start, gateway errors). */
class ServerUnavailableException(code: Int) : IOException("Backend returned HTTP $code")

object ErrorMessages {
    const val NO_INTERNET = "No internet connection. Check your network and try again."
    const val GENERIC = "Something went wrong. Please try again."

    /** Turns any throwable from the download pipeline into a short, human sentence. */
    fun from(error: Throwable, hasInternet: Boolean = true): String {
        if (error is GrabException) return error.userMessage
        if (!hasInternet) return NO_INTERNET
        return when (error) {
            is ServerUnavailableException -> "The server is busy or starting up. Please try again in a minute."
            is SocketTimeoutException -> "The server took too long to respond. Please try again."
            is UnknownHostException, is ConnectException ->
                "Couldn't reach the server. Check your connection or the backend URL in settings."
            is IOException -> "The connection was interrupted. Please try again."
            else -> GENERIC
        }
    }

    /** Maps the raw `error` string returned by the backend's /extract endpoint. */
    fun fromBackend(raw: String?, httpCode: Int): String {
        val msg = raw.orEmpty().lowercase()
        return when {
            httpCode == 429 || "too many requests" in msg || "rate limit" in msg ->
                "Too many requests right now. Please wait a bit and try again."
            "bot detection" in msg || "confirm you" in msg || "not a bot" in msg ->
                "YouTube is temporarily blocking downloads. Please try again later."
            "cookies" in msg || "login" in msg || "log in" in msg || "sign in" in msg || "age-restricted" in msg || "age restricted" in msg ->
                "This video needs a login. It may be private or age-restricted."
            "unsupported url" in msg ->
                "This link isn't supported. Use a YouTube, X, Instagram, or Facebook video link."
            "no playable" in msg || "no video" in msg || "no downloadable" in msg ->
                "No downloadable video was found in this post."
            "private" in msg || "unavailable" in msg || "removed" in msg || "deleted" in msg ||
                "not found" in msg || httpCode == 404 ->
                "This video is private, deleted, or unavailable."
            else -> "Couldn't get this video. Please check the link and try again."
        }
    }

    fun forDownloadHttp(code: Int): String = when (code) {
        401, 403 -> "Access denied. The video may be private or the link expired."
        404, 410 -> "Video not found. It may have been deleted."
        429 -> "Too many requests right now. Please wait a bit and try again."
        in 500..599 -> "The server had a problem sending the video. Please try again."
        else -> "Download failed (HTTP $code). Please try again."
    }
}

object FileNames {
    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val whitespace = Regex("""\s+""")

    /** Keeps the title readable (including non-Latin scripts) while making it filesystem-safe. */
    fun sanitize(title: String?, fallback: String = "video", maxLength: Int = 80): String {
        val cleaned = title.orEmpty()
            .replace(illegal, " ")
            .replace(whitespace, " ")
            .trim()
            .trim('.', ' ', '_', '-')
            .take(maxLength)
            .trim()
        return cleaned.ifEmpty { fallback }
    }
}
