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
    fun testOtherTabsAreNotVisible() {
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("MOVIES"))
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("SERIES"))
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("SEARCH"))
        assertFalse(LiveContentSurfaceVisibilityPolicy.isVisible("UNKNOWN"))
    }
}
