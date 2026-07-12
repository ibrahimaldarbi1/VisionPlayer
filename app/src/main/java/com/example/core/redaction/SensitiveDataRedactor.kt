package com.example.core.redaction

object SensitiveDataRedactor {

    /**
     * Extracts and redacts a URL, keeping only the scheme, host, and port if safe,
     * while removing user info, path segments, and query parameters.
     */
    fun redactUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val trimmed = url.trim()
        if (trimmed.contains(" ")) return "redacted-host"

        val lower = trimmed.lowercase()
        val hasHttpScheme = lower.startsWith("http://") || lower.startsWith("https://")
        
        if (hasHttpScheme) {
            val match = Regex("^(?i)(https?://)([^/?#]+)(.*)$").find(trimmed)
            if (match == null) {
                return "redacted-host"
            }
            val scheme = match.groupValues[1].lowercase()
            var authority = match.groupValues[2]
            
            if (authority.contains("@")) {
                authority = authority.substringAfterLast("@")
            }
            
            val resultHost = cleanAndValidateHostPort(authority)
            if (resultHost == null) {
                return "redacted-host"
            }
            return "$scheme$resultHost"
        } else {
            if (trimmed.contains("/") || trimmed.contains("?") || trimmed.contains("#") || trimmed.contains("=") || trimmed.contains("&")) {
                return "redacted-host"
            }
            
            val resultHost = cleanAndValidateHostPort(trimmed)
            if (resultHost == null) {
                return "redacted-host"
            }
            return resultHost
        }
    }

    private fun cleanAndValidateHostPort(input: String): String? {
        val lower = input.lowercase()
        if (lower == "player_api.php" || lower == "xmltv.php" || lower.endsWith(".php")) {
            return null
        }
        
        val colonIndex = input.lastIndexOf(':')
        val host: String
        val port: String
        
        if (colonIndex >= 0 && colonIndex > input.indexOf(']')) {
            host = input.substring(0, colonIndex)
            port = input.substring(colonIndex + 1)
            if (!port.all { it.isDigit() }) {
                return null
            }
        } else {
            host = input
            port = ""
        }
        
        if (isValidIPv4(host) || isValidIPv6(host) || isValidDnsHost(host)) {
            return if (port.isNotEmpty()) "$host:$port" else host
        }
        
        return null
    }

    private fun isValidIPv4(host: String): Boolean {
        return Regex("^(\\d{1,3}\\.){3}\\d{1,3}$").matches(host) && host.split('.').all { 
            val num = it.toIntOrNull()
            num != null && num in 0..255 
        }
    }

    private fun isValidIPv6(host: String): Boolean {
        return host.startsWith("[") && host.endsWith("]") && host.length > 2 && host.substring(1, host.length - 1).all {
            it.isLetterOrDigit() || it == ':' || it == '.'
        }
    }

    private fun isValidDnsHost(host: String): Boolean {
        return Regex("^([a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+$").matches(host)
    }

    /**
     * Extracts only the host from a URL (e.g. provider.example.com).
     */
    fun redactHost(url: String?): String {
        if (url.isNullOrBlank()) return "Not signed in"
        val redactedUrl = redactUrl(url)
        if (redactedUrl == "redacted-host") return "redacted-host"
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

        val urlRegex = Regex("(?i)https?://[^\\s\"'<>]+")
        redacted = urlRegex.replace(redacted) { match ->
            redactUrl(match.value)
        }

        val xtreamPathRegex = Regex("(?i)/(live|movie|series)/[^/\\s'\"]+/[^/\\s'\"]+[^\\s\"'<>]*(/|$)")
        redacted = redacted.replace(xtreamPathRegex, "/[redacted-stream-path]")

        val playerApiRegex = Regex("(?i)\\bplayer_api\\.php[^\\s\"'<>/]*")
        redacted = redacted.replace(playerApiRegex, "[redacted-api-call]")

        val xmltvRegex = Regex("(?i)\\bxmltv\\.php[^\\s\"'<>/]*")
        redacted = redacted.replace(xmltvRegex, "[redacted-xmltv-call]")

        val credentialPatterns = listOf(
            Regex("(?i)\\b(username|user|usr|password|pass|pwd|token|tok|xmltv|streamurl)\\s*[:=]\\s*[^&\\s\"'<>]+")
        )
        for (pattern in credentialPatterns) {
            redacted = redacted.replace(pattern) { match ->
                val key = match.groupValues[1]
                "$key=[redacted]"
            }
        }
        
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
