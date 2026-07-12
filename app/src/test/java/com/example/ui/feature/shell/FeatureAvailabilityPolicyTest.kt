package com.example.ui.feature.shell

import com.example.config.FeatureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureAvailabilityPolicyTest {

    private val allDisabled = FeatureConfig(
        liveTvEnabled = false,
        epgEnabled = false,
        moviesEnabled = false,
        seriesEnabled = false,
        searchEnabled = false,
        multiViewEnabled = false,
        supportPageEnabled = false,
        parentalControlEnabled = false,
        footballScheduleEnabled = false,
        favoritesEnabled = false,
        recentlyWatchedEnabled = false,
        continueWatchingEnabled = false,
        externalPlayerEnabled = false,
        pictureInPictureEnabled = false,
        mediaSessionEnabled = false,
        footballChannelOverridesEnabled = false
    )

    private val allEnabled = FeatureConfig(
        liveTvEnabled = true,
        epgEnabled = true,
        moviesEnabled = true,
        seriesEnabled = true,
        searchEnabled = true,
        multiViewEnabled = true,
        supportPageEnabled = true,
        parentalControlEnabled = true,
        footballScheduleEnabled = true,
        favoritesEnabled = true,
        recentlyWatchedEnabled = true,
        continueWatchingEnabled = true,
        externalPlayerEnabled = true,
        pictureInPictureEnabled = true,
        mediaSessionEnabled = true,
        footballChannelOverridesEnabled = true
    )

    @Test
    fun homeIsAlwaysEnabled() {
        assertTrue(FeatureAvailabilityPolicy.phoneDestinations(allDisabled).contains(AppDestination.HOME))
        assertTrue(FeatureAvailabilityPolicy.tvDestinations(allDisabled).contains(AppDestination.HOME))
    }

    @Test
    fun settingsIsAlwaysEnabled() {
        assertTrue(FeatureAvailabilityPolicy.phoneDestinations(allDisabled).contains(AppDestination.SETTINGS))
        assertTrue(FeatureAvailabilityPolicy.tvDestinations(allDisabled).contains(AppDestination.SETTINGS))
    }

    @Test
    fun disabledLiveIsAbsentFromPhoneDestinations() {
        assertFalse(FeatureAvailabilityPolicy.phoneDestinations(allDisabled).contains(AppDestination.LIVE))
    }

    @Test
    fun disabledLiveIsAbsentFromTvDestinations() {
        assertFalse(FeatureAvailabilityPolicy.tvDestinations(allDisabled).contains(AppDestination.LIVE))
    }

    @Test
    fun disabledMoviesIsAbsentFromPhoneDestinations() {
        assertFalse(FeatureAvailabilityPolicy.phoneDestinations(allDisabled).contains(AppDestination.MOVIES))
    }

    @Test
    fun disabledMoviesIsAbsentFromTvDestinations() {
        assertFalse(FeatureAvailabilityPolicy.tvDestinations(allDisabled).contains(AppDestination.MOVIES))
    }

    @Test
    fun disabledSeriesIsAbsentFromPhoneDestinations() {
        assertFalse(FeatureAvailabilityPolicy.phoneDestinations(allDisabled).contains(AppDestination.SERIES))
    }

    @Test
    fun disabledSeriesIsAbsentFromTvDestinations() {
        assertFalse(FeatureAvailabilityPolicy.tvDestinations(allDisabled).contains(AppDestination.SERIES))
    }

    @Test
    fun epgIsEnabledWhenBothLiveAndEpgAreEnabled() {
        assertTrue(FeatureAvailabilityPolicy.tvDestinations(allEnabled).contains(AppDestination.EPG))
    }

    @Test
    fun epgIsDisabledWhenLiveIsDisabled() {
        val config = allEnabled.copy(liveTvEnabled = false)
        assertFalse(FeatureAvailabilityPolicy.tvDestinations(config).contains(AppDestination.EPG))
    }

    @Test
    fun epgIsDisabledWhenTheEpgFlagIsDisabled() {
        val config = allEnabled.copy(epgEnabled = false)
        assertFalse(FeatureAvailabilityPolicy.tvDestinations(config).contains(AppDestination.EPG))
    }

    @Test
    fun searchAvailabilityFollowsSearchEnabled() {
        assertTrue(FeatureAvailabilityPolicy.isDestinationEnabled(AppDestination.SEARCH, allEnabled))
        assertFalse(FeatureAvailabilityPolicy.isDestinationEnabled(AppDestination.SEARCH, allDisabled))
    }

    @Test
    fun invalidActiveLiveFallsBackToHome() {
        assertEquals(AppDestination.HOME, FeatureAvailabilityPolicy.sanitizeDestination(AppDestination.LIVE, allDisabled))
    }

    @Test
    fun invalidActiveMoviesFallsBackToHome() {
        assertEquals(AppDestination.HOME, FeatureAvailabilityPolicy.sanitizeDestination(AppDestination.MOVIES, allDisabled))
    }

    @Test
    fun invalidActiveSeriesFallsBackToHome() {
        assertEquals(AppDestination.HOME, FeatureAvailabilityPolicy.sanitizeDestination(AppDestination.SERIES, allDisabled))
    }

    @Test
    fun invalidActiveEpgFallsBackToHome() {
        assertEquals(AppDestination.HOME, FeatureAvailabilityPolicy.sanitizeDestination(AppDestination.EPG, allDisabled))
    }

    @Test
    fun validRequestedDestinationRemainsUnchanged() {
        assertEquals(AppDestination.LIVE, FeatureAvailabilityPolicy.sanitizeDestination(AppDestination.LIVE, allEnabled))
        assertEquals(AppDestination.HOME, FeatureAvailabilityPolicy.sanitizeDestination(AppDestination.HOME, allDisabled))
        assertEquals(AppDestination.SETTINGS, FeatureAvailabilityPolicy.sanitizeDestination(AppDestination.SETTINGS, allDisabled))
    }

    @Test
    fun multiViewHelperFollowsMultiViewEnabled() {
        assertTrue(FeatureAvailabilityPolicy.shouldShowMultiView(allEnabled))
        assertFalse(FeatureAvailabilityPolicy.shouldShowMultiView(allDisabled))
    }

    @Test
    fun supportHelperFollowsSupportPageEnabled() {
        assertTrue(FeatureAvailabilityPolicy.shouldShowSupport(allEnabled))
        assertFalse(FeatureAvailabilityPolicy.shouldShowSupport(allDisabled))
    }

    @Test
    fun parentalHelperFollowsParentalControlEnabled() {
        assertTrue(FeatureAvailabilityPolicy.shouldShowParentalControls(allEnabled))
        assertFalse(FeatureAvailabilityPolicy.shouldShowParentalControls(allDisabled))
    }

    @Test
    fun footballHelperFollowsFootballScheduleEnabled() {
        assertTrue(FeatureAvailabilityPolicy.shouldShowFootball(allEnabled))
        assertFalse(FeatureAvailabilityPolicy.shouldShowFootball(allDisabled))
    }

    @Test
    fun favoritesCollectionHelperFollowsFavoritesEnabled() {
        assertTrue(FeatureAvailabilityPolicy.shouldCollectFavorites(allEnabled))
        assertFalse(FeatureAvailabilityPolicy.shouldCollectFavorites(allDisabled))
    }

    @Test
    fun recentlyWatchedHelperFollowsRecentlyWatchedEnabled() {
        assertTrue(FeatureAvailabilityPolicy.shouldCollectRecentlyWatched(allEnabled))
        assertFalse(FeatureAvailabilityPolicy.shouldCollectRecentlyWatched(allDisabled))
    }

    @Test
    fun continueWatchingHelperFollowsContinueWatchingEnabled() {
        assertTrue(FeatureAvailabilityPolicy.shouldCollectContinueWatching(allEnabled))
        assertFalse(FeatureAvailabilityPolicy.shouldCollectContinueWatching(allDisabled))
    }
}
