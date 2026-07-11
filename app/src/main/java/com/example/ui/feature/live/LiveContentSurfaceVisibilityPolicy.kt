package com.example.ui.feature.live

import com.example.ui.feature.shell.AppDestination

internal object LiveContentSurfaceVisibilityPolicy {
    fun isVisible(activeDestination: AppDestination): Boolean {
        return activeDestination == AppDestination.LIVE || activeDestination == AppDestination.EPG
    }
}
