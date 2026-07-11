package com.example.ui.feature.live

internal object LiveContentSurfaceVisibilityPolicy {

    fun isVisible(activeTab: String): Boolean {
        return activeTab == "LIVE" || activeTab == "EPG"
    }
}
