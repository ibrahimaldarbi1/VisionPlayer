package com.example.ui.feature.live

import com.example.ui.feature.shell.AppDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveContentSurfaceVisibilityPolicyTest {

    @Test
    fun isVisible_liveTab_returnsTrue() {
        val result = LiveContentSurfaceVisibilityPolicy.isVisible(AppDestination.LIVE)
        assertTrue("Live tab should be visible", result)
    }

    @Test
    fun isVisible_epgTab_returnsTrue() {
        val result = LiveContentSurfaceVisibilityPolicy.isVisible(AppDestination.EPG)
        assertTrue("EPG tab should be visible", result)
    }

    @Test
    fun isVisible_homeTab_returnsFalse() {
        val result = LiveContentSurfaceVisibilityPolicy.isVisible(AppDestination.HOME)
        assertFalse("Home tab should not be visible", result)
    }

    @Test
    fun isVisible_moviesTab_returnsFalse() {
        val result = LiveContentSurfaceVisibilityPolicy.isVisible(AppDestination.MOVIES)
        assertFalse("Movies tab should not be visible", result)
    }

    @Test
    fun isVisible_seriesTab_returnsFalse() {
        val result = LiveContentSurfaceVisibilityPolicy.isVisible(AppDestination.SERIES)
        assertFalse("Series tab should not be visible", result)
    }

    @Test
    fun isVisible_searchTab_returnsFalse() {
        val result = LiveContentSurfaceVisibilityPolicy.isVisible(AppDestination.SEARCH)
        assertFalse("Search tab should not be visible", result)
    }

    @Test
    fun isVisible_settingsTab_returnsFalse() {
        val result = LiveContentSurfaceVisibilityPolicy.isVisible(AppDestination.SETTINGS)
        assertFalse("Settings tab should not be visible", result)
    }
}
