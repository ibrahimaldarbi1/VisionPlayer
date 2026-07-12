package com.example.config

import com.example.BuildConfig

object DemoPolicy {
    /**
     * Centralized decision of whether demo mode is permitted on this build.
     * Must be true ONLY when both BuildConfig.DEBUG is true and BuildConfig.DEBUG_DEMO_MODE_ENABLED is true.
     */
    val isDemoModeAllowed: Boolean
        get() = BuildConfig.DEBUG && BuildConfig.DEBUG_DEMO_MODE_ENABLED

    /**
     * Determines whether a given session is an authorized demo session.
     * It is ONLY authorized if isDemoModeAllowed is true AND the credentials match exactly.
     */
    fun isDemoSession(username: String?, serverUrl: String?): Boolean {
        if (!isDemoModeAllowed) return false
        if (username == null || serverUrl == null) return false
        
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val cleanUser = username.trim()
        
        // Exact matching, no substring heuristics.
        val isExactDemoUser = cleanUser == "demo_user"
        val isExactDemoUrl = cleanUrl == "https://demo.iptvserver.net" || cleanUrl == "http://demo.iptvserver.net"
        
        return isExactDemoUser && isExactDemoUrl
    }
}
