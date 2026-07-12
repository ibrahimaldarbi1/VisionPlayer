package com.example.ui.feature.shell

import com.example.config.FeatureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigationStateTest {

    private val allEnabled = FeatureConfig(
        liveTvEnabled = true,
        epgEnabled = true,
        moviesEnabled = true,
        seriesEnabled = true,
        searchEnabled = true
    )

    @Test
    fun initialDestinationIsHome() {
        val state = AppNavigationState(AppDestination.HOME)
        assertEquals(AppDestination.HOME, state.activeDestination)
    }

    @Test
    fun navigationToEnabledLiveSucceeds() {
        val state = AppNavigationState(AppDestination.HOME)
        state.navigateTo(AppDestination.LIVE, allEnabled)
        assertEquals(AppDestination.LIVE, state.activeDestination)
    }

    @Test
    fun navigationToDisabledDestinationFallsBackToHome() {
        val state = AppNavigationState(AppDestination.HOME)
        val disabled = allEnabled.copy(moviesEnabled = false)
        state.navigateTo(AppDestination.MOVIES, disabled)
        assertEquals(AppDestination.HOME, state.activeDestination)
    }

    @Test
    fun featureChangeInvalidatesActiveMovies() {
        val state = AppNavigationState(AppDestination.HOME)
        state.navigateTo(AppDestination.MOVIES, allEnabled)
        assertEquals(AppDestination.MOVIES, state.activeDestination)
        
        state.onFeaturesChanged(allEnabled.copy(moviesEnabled = false))
        assertEquals(AppDestination.HOME, state.activeDestination)
    }

    @Test
    fun featureChangeInvalidatesActiveSeries() {
        val state = AppNavigationState(AppDestination.HOME)
        state.navigateTo(AppDestination.SERIES, allEnabled)
        assertEquals(AppDestination.SERIES, state.activeDestination)
        
        state.onFeaturesChanged(allEnabled.copy(seriesEnabled = false))
        assertEquals(AppDestination.HOME, state.activeDestination)
    }

    @Test
    fun featureChangeInvalidatesActiveLive() {
        val state = AppNavigationState(AppDestination.HOME)
        state.navigateTo(AppDestination.LIVE, allEnabled)
        assertEquals(AppDestination.LIVE, state.activeDestination)
        
        state.onFeaturesChanged(allEnabled.copy(liveTvEnabled = false))
        assertEquals(AppDestination.HOME, state.activeDestination)
    }

    @Test
    fun featureChangeInvalidatesActiveEpg() {
        val state = AppNavigationState(AppDestination.HOME)
        state.navigateTo(AppDestination.EPG, allEnabled)
        assertEquals(AppDestination.EPG, state.activeDestination)
        
        state.onFeaturesChanged(allEnabled.copy(epgEnabled = false))
        assertEquals(AppDestination.HOME, state.activeDestination)
    }

    @Test
    fun featureChangePreservesSettings() {
        val state = AppNavigationState(AppDestination.HOME)
        state.navigateTo(AppDestination.SETTINGS, allEnabled)
        assertEquals(AppDestination.SETTINGS, state.activeDestination)
        
        val allDisabled = FeatureConfig(
            liveTvEnabled = false,
            epgEnabled = false,
            moviesEnabled = false,
            seriesEnabled = false,
            searchEnabled = false
        )
        state.onFeaturesChanged(allDisabled)
        assertEquals(AppDestination.SETTINGS, state.activeDestination)
    }

    @Test
    fun reenablingFeatureDoesNotAutomaticallyNavigateToIt() {
        val state = AppNavigationState(AppDestination.HOME)
        state.navigateTo(AppDestination.LIVE, allEnabled)
        
        // Disable
        state.onFeaturesChanged(allEnabled.copy(liveTvEnabled = false))
        assertEquals(AppDestination.HOME, state.activeDestination)
        
        // Re-enable
        state.onFeaturesChanged(allEnabled)
        assertEquals(AppDestination.HOME, state.activeDestination)
    }

    @Test
    fun unknownDestinationKeyReturnsNull() {
        assertNull(AppDestination.fromKey("UNKNOWN_KEY"))
    }

    @Test
    fun destinationKeyParsingIsCaseSensitive() {
        assertNull(AppDestination.fromKey("home"))
        assertEquals(AppDestination.HOME, AppDestination.fromKey("HOME"))
    }
}
