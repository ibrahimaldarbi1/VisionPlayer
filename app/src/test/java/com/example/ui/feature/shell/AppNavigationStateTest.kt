package com.example.ui.feature.shell

import com.example.config.FeatureConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationStateTest {

    @Test
    fun testNavigateToValidDestination() {
        val state = AppNavigationState(AppDestination.HOME)
        val features = FeatureConfig(liveTvEnabled = true)

        state.navigateTo(AppDestination.LIVE, features)
        assertEquals(AppDestination.LIVE, state.activeDestination)
    }

    @Test
    fun testNavigateToInvalidDestination_fallsBackToHome() {
        val state = AppNavigationState(AppDestination.HOME)
        val features = FeatureConfig(liveTvEnabled = false) // Live TV disabled

        state.navigateTo(AppDestination.LIVE, features)
        assertEquals(AppDestination.HOME, state.activeDestination)
    }

    @Test
    fun testOnFeaturesChanged_removesInvalidDestination() {
        val state = AppNavigationState(AppDestination.HOME)
        
        // Start with live enabled, navigate to it
        var features = FeatureConfig(liveTvEnabled = true)
        state.navigateTo(AppDestination.LIVE, features)
        assertEquals(AppDestination.LIVE, state.activeDestination)

        // Disable live, and simulate feature change
        features = FeatureConfig(liveTvEnabled = false)
        state.onFeaturesChanged(features)

        // Should fall back to Home since Live is no longer available
        assertEquals(AppDestination.HOME, state.activeDestination)
    }
}
