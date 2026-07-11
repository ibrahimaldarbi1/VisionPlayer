package com.example.ui.feature.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveContentSurfaceVisibilityPolicyTest {

    @Test
    fun testLiveTabIsVisible() {
        assertTrue(LiveContentSurfaceVisibilityPolicy.isVisible("LIVE"))
    }

    @Test
    fun testEpgTabIsVisible() {
        assertTrue(LiveContentSurfaceVisibilityPolicy.isVisible("EPG"))
    }

    @Test
    fun testHomeTabIsNotVisible() {
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("HOME"))
    }

    @Test
    fun testMoviesTabIsNotVisible() {
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("MOVIES"))
    }

    @Test
    fun testSeriesTabIsNotVisible() {
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("SERIES"))
    }

    @Test
    fun testSearchTabIsNotVisible() {
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("SEARCH"))
    }

    @Test
    fun testSettingsTabIsNotVisible() {
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("SETTINGS"))
    }

    @Test
    fun testMatchingIsCaseSensitive() {
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("live"))
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("epg"))
    }
}
