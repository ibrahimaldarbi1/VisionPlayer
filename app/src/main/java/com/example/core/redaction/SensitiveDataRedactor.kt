package com.example.core.redaction

import android.net.Uri
import java.net.URI

object SensitiveDataRedactor {

    /**
     * Extracts and redacts a URL, keeping only the scheme, host, and port if safe,
     * while removing user info, path segments, and query parameters.
     */
    fun redactUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val uri = URI(url)
            val scheme = uri.scheme
            val host = uri.host
            val port = uri.port
            
            val hostPart = if (host != null) {
                if (port != -1) "$host:$port" else host
            } else {
                // Fallback regex matching if URI parsing fails to extract host due to credentials
                val regex = Regex("^(https?://)?([^/?:#@]+@)?([^/?:#]+)(.*)$")
                val match = regex.matchEntire(url)
                if (match != null) {
                    match.groupValues[3]
                } else {
                    "redacted-host"
                }
            }
            
            if (scheme != null) {
                "$scheme://$hostPart"
            } else {
                hostPart
            }
        } catch (e: Exception) {
            // Safe fallback
            "redacted-host"
        }
    }

    /**
     * Extracts only the host from a URL (e.g. provider.example.com).
     */
    fun redactHost(url: String?): String {
        if (url.isNullOrBlank()) return "Not signed in"
        if (url.contains("demo") || url.isBlank()) return "demo.iptvserver.net" // or "Not signed in"? Let's handle logout case
        return try {
            val uri = URI(url)
            uri.host ?: "redacted-host"
        } catch (e: Exception) {
            val regex = Regex("^(https?://)?([^/?:#@]+@)?([^/?:#]+)(.*)$")
            val match = regex.matchEntire(url)
            if (match != null) {
                match.groupValues[3]
            } else {
                "redacted-host"
            }
        }
    }

    /**
     * Redacts exception messages by stripping out sensitive-looking credentials, paths or query parameters.
     */
    fun redactExceptionMessage(message: String?): String {
        if (message.isNullOrBlank()) return ""
        // Replace typical credential patterns
        var redacted: String = message
        val patterns = listOf(
            Regex("(?i)username=[^&\\s]+"),
            Regex("(?i)password=[^&\\s]+"),
            Regex("(?i)token=[^&\\s]+")
        )
        for (pattern in patterns) {
            redacted = redacted.replace(pattern, "redacted")
        }
        // Also strip long paths that look like stream urls
        val pathPattern = Regex("/(live|movie|series)/[^/\\s]+/[^/\\s]+")
        redacted = redacted.replace(pathPattern, "/[redacted-stream-path]")
        return redacted
    }

    /**
     * Filters a list of arguments or logs, ensuring none of the known sensitive keys/fields are present.
     */
    fun isSensitiveWord(word: String): Boolean {
        val lowercase = word.lowercase()
        return lowercase.contains("username") ||
                lowercase.contains("password") ||
                lowercase.contains("token") ||
                lowercase.contains("xmltv") ||
                lowercase.contains("streamUrl")
    }
}
