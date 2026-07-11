package com.example.ui.feature.shell

import com.example.config.FeatureConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureAvailabilityPolicyTest {

    @Test
    fun testAllFeaturesEnabled() {
        val features = FeatureConfig(
            liveTvEnabled = true,
            moviesEnabled = true,
            seriesEnabled = true,
            epgEnabled = true,
            searchEnabled = true,
            footballChannelOverridesEnabled = true
        )

        val phoneDestinations = FeatureAvailabilityPolicy.phoneDestinations(features)
        assertEquals(listOf(AppDestination.HOME, AppDestination.LIVE, AppDestination.MOVIES, AppDestination.SERIES, AppDestination.SETTINGS), phoneDestinations)

        val tvDestinations = FeatureAvailabilityPolicy.tvDestinations(features)
        assertEquals(listOf(AppDestination.HOME, AppDestination.LIVE, AppDestination.EPG, AppDestination.MOVIES, AppDestination.SERIES, AppDestination.SETTINGS), tvDestinations)
    }

    @Test
    fun testOnlyLiveEnabled() {
        val features = FeatureConfig(
            liveTvEnabled = true,
            moviesEnabled = false,
            seriesEnabled = false,
            epgEnabled = false,
            searchEnabled = false
        )

        val tvDestinations = FeatureAvailabilityPolicy.tvDestinations(features)
        assertEquals(listOf(AppDestination.HOME, AppDestination.LIVE, AppDestination.SETTINGS), tvDestinations)
    }

    @Test
    fun testSanitizeDestination() {
        val features = FeatureConfig(
            liveTvEnabled = false,
            moviesEnabled = false,
            seriesEnabled = false,
            epgEnabled = false,
            searchEnabled = false
        )

        val sanitized = FeatureAvailabilityPolicy.sanitizeDestination(AppDestination.LIVE, features)
        assertEquals(AppDestination.HOME, sanitized)
    }
}
