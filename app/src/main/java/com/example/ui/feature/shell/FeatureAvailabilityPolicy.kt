package com.example.ui.feature.shell

import com.example.config.FeatureConfig

object FeatureAvailabilityPolicy {

    fun isDestinationEnabled(
        destination: AppDestination,
        features: FeatureConfig
    ): Boolean {
        return when (destination) {
            AppDestination.HOME -> true
            AppDestination.LIVE -> features.liveTvEnabled
            AppDestination.MOVIES -> features.moviesEnabled
            AppDestination.SERIES -> features.seriesEnabled
            AppDestination.EPG -> features.liveTvEnabled && features.epgEnabled
            AppDestination.SEARCH -> features.searchEnabled
            AppDestination.SETTINGS -> true
        }
    }

    fun phoneDestinations(
        features: FeatureConfig
    ): List<AppDestination> {
        val list = mutableListOf(AppDestination.HOME)
        if (features.liveTvEnabled) list.add(AppDestination.LIVE)
        if (features.moviesEnabled) list.add(AppDestination.MOVIES)
        if (features.seriesEnabled) list.add(AppDestination.SERIES)
        list.add(AppDestination.SETTINGS)
        return list
    }

    fun tvDestinations(
        features: FeatureConfig
    ): List<AppDestination> {
        val list = mutableListOf(AppDestination.HOME)
        if (features.liveTvEnabled) list.add(AppDestination.LIVE)
        if (features.liveTvEnabled && features.epgEnabled) list.add(AppDestination.EPG)
        if (features.moviesEnabled) list.add(AppDestination.MOVIES)
        if (features.seriesEnabled) list.add(AppDestination.SERIES)
        if (features.searchEnabled) list.add(AppDestination.SEARCH)
        list.add(AppDestination.SETTINGS)
        return list
    }

    fun firstEnabledDestination(
        features: FeatureConfig
    ): AppDestination {
        return AppDestination.HOME
    }

    fun sanitizeDestination(
        requested: AppDestination,
        features: FeatureConfig
    ): AppDestination {
        return if (isDestinationEnabled(requested, features)) {
            requested
        } else {
            firstEnabledDestination(features)
        }
    }

    fun shouldShowSupport(
        features: FeatureConfig
    ): Boolean = features.supportPageEnabled

    fun shouldShowParentalControls(
        features: FeatureConfig
    ): Boolean = features.parentalControlEnabled

    fun shouldShowMultiView(
        features: FeatureConfig
    ): Boolean = features.multiViewEnabled

    fun shouldShowFootball(
        features: FeatureConfig
    ): Boolean = features.footballScheduleEnabled

    fun shouldCollectFavorites(
        features: FeatureConfig
    ): Boolean = features.favoritesEnabled

    fun shouldCollectRecentlyWatched(
        features: FeatureConfig
    ): Boolean = features.recentlyWatchedEnabled

    fun shouldCollectContinueWatching(
        features: FeatureConfig
    ): Boolean = features.continueWatchingEnabled
}
