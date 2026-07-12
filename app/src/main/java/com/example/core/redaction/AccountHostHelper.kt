package com.example.core.redaction

object AccountHostHelper {
    /**
     * Centralized account-host presentation helper.
     * Takes the raw server URL and returns the safely redacted active account host.
     * Must not fallback to "demo.iptvserver.net" if the user is not signed in or has a blank URL.
     * It should return a default clean indicator like "Not signed in" or empty/redacted string.
     */
    fun formatRedactedHost(serverUrl: String?): String {
        if (serverUrl.isNullOrBlank()) {
            return "Not signed in"
        }
        val host = SensitiveDataRedactor.redactHost(serverUrl)
        if (host.isBlank() || host == "redacted-host") {
            return "Unknown Provider"
        }
        return host
    }
}
