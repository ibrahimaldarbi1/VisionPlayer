package com.example.core.redaction

import java.net.URI

object SensitiveDataRedactor {

    /**
     * Extracts and redacts a URL, keeping only the scheme, host, and port if safe,
     * while removing user info, path segments, and query parameters.
     */
    fun redactUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val trimmed = url.trim()
        return try {
            // 1. Extract scheme (http or https)
            var scheme = "http://"
            var rest = trimmed
            val schemeMatch = Regex("^(?i)(https?://)").find(trimmed)
            if (schemeMatch != null) {
                scheme = schemeMatch.value.lowercase()
                rest = trimmed.substring(schemeMatch.range.last + 1)
            } else {
                if (trimmed.contains("://")) {
                    scheme = trimmed.substringBefore("://").lowercase() + "://"
                    rest = trimmed.substringAfter("://")
                }
            }
            
            // 2. Strip userinfo (e.g., user:pass@host)
            if (rest.contains("@")) {
                val firstSlash = rest.indexOf('/')
                val firstQuestion = rest.indexOf('?')
                val firstHash = rest.indexOf('#')
                val boundary = listOf(firstSlash, firstQuestion, firstHash).filter { it >= 0 }.minOrNull() ?: rest.length
                val authority = rest.substring(0, boundary)
                if (authority.contains("@")) {
                    rest = rest.substring(authority.lastIndexOf('@') + 1)
                }
            }
            
            // 3. Now rest starts with host (and port) followed by path/query/fragment
            val endOfHost = listOf(rest.indexOf('/'), rest.indexOf('?'), rest.indexOf('#'))
                .filter { it >= 0 }
                .minOrNull() ?: rest.length
            
            val hostAndPort = rest.substring(0, endOfHost)
            
            // 4. Split host and port
            val colonIndex = hostAndPort.lastIndexOf(':')
            val host = if (colonIndex >= 0 && colonIndex > hostAndPort.indexOf(']')) {
                val portStr = hostAndPort.substring(colonIndex + 1)
                if (portStr.all { it.isDigit() }) {
                    hostAndPort.substring(0, colonIndex)
                } else {
                    hostAndPort
                }
            } else {
                hostAndPort
            }
            
            val portPart = if (colonIndex >= 0 && colonIndex > hostAndPort.indexOf(']')) {
                val portStr = hostAndPort.substring(colonIndex + 1)
                if (portStr.all { it.isDigit() }) ":$portStr" else ""
            } else {
                ""
            }
            
            "$scheme$host$portPart"
        } catch (e: Exception) {
            "redacted-host"
        }
    }

    /**
     * Extracts only the host from a URL (e.g. provider.example.com).
     */
    fun redactHost(url: String?): String {
        if (url.isNullOrBlank()) return "Not signed in"
        val redactedUrl = redactUrl(url)
        return try {
            val schemeIndex = redactedUrl.indexOf("://")
            val hostAndPort = if (schemeIndex >= 0) {
                redactedUrl.substring(schemeIndex + 3)
            } else {
                redactedUrl
            }
            val colonIndex = hostAndPort.indexOf(':')
            if (colonIndex >= 0) {
                hostAndPort.substring(0, colonIndex)
            } else {
                hostAndPort
            }
        } catch (e: Exception) {
            "redacted-host"
        }
    }

    /**
     * Redacts exception messages by stripping out sensitive-looking credentials, paths or query parameters.
     */
    fun redactExceptionMessage(message: String?): String {
        if (message.isNullOrBlank()) return ""
        var redacted: String = message

        // 1. Redact any embedded URLs starting with http:// or https://
        val urlRegex = Regex("(?i)https?://[^\\s\"'<>]+")
        redacted = urlRegex.replace(redacted) { match ->
            redactUrl(match.value)
        }

        // 2. Redact any paths that look like xtream credentials or paths
        // e.g. /live/username/password/123 or /movie/username/password/123 or /series/username/password/123
        val xtreamPathRegex = Regex("(?i)/(live|movie|series)/[^/\\s'\"]+/[^/\\s'\"]+[^\\s\"'<>]*(/|$)")
        redacted = redacted.replace(xtreamPathRegex, "/[redacted-stream-path]")

        // 3. Remove files or scripts
        val playerApiRegex = Regex("(?i)\\bplayer_api\\.php[^\\s\"'<>/]*")
        redacted = redacted.replace(playerApiRegex, "[redacted-api-call]")

        val xmltvRegex = Regex("(?i)\\bxmltv\\.php[^\\s\"'<>/]*")
        redacted = redacted.replace(xmltvRegex, "[redacted-xmltv-call]")

        // 4. Redact any credentials and case variations of username, password, token
        val credentialPatterns = listOf(
            Regex("(?i)\\b(username|user|usr|password|pass|pwd|token|tok|xmltv|streamurl)\\s*[:=]\\s*[^&\\s\"'<>]+")
        )
        for (pattern in credentialPatterns) {
            redacted = redacted.replace(pattern) { match ->
                val key = match.groupValues[1]
                "$key=[redacted]"
            }
        }
        
        // Also query parameters: e.g. ?username=... or &password=... or ?token=... or &token=...
        val queryParamPatterns = listOf(
            Regex("(?i)([?&])(username|password|token|xmltv|streamurl)=[^&\\s\"'<>]*")
        )
        for (pattern in queryParamPatterns) {
            redacted = redacted.replace(pattern) { match ->
                val delimiter = match.groupValues[1]
                val key = match.groupValues[2]
                "$delimiter$key=[redacted]"
            }
        }

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
                lowercase.contains("streamurl")
    }
}
