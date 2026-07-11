package com.example.ui.feature.live

import com.example.data.Category
import com.example.data.LiveChannel

data class LiveUiState(
    val featureEnabled: Boolean = false,
    val providerId: String? = null,
    val categories: List<Category> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val selectedCategoryId: String? = null,
    val initialLoading: Boolean = false,
    val categoriesRefreshing: Boolean = false,
    val channelsRefreshing: Boolean = false,
    val categoriesLoading: Boolean = false,
    val channelsLoading: Boolean = false,
    val categoriesError: String? = null,
    val channelsError: String? = null,
    val hasLoadedCategories: Boolean = false,
    val hasLoadedChannels: Boolean = false,
    val categoryVisibilityReady: Boolean = false,
    val favoritesEnabled: Boolean = false,
    val favoriteChannelIds: Set<String> = emptySet(),
    val favoriteMutationChannelIds: Set<String> = emptySet(),
    val favoritesLoading: Boolean = false,
    val favoritesLoadError: String? = null,
    val favoriteMutationError: String? = null,

    val parentalControlsEnabled: Boolean = false,
    val parentalReady: Boolean = false,
    val parentalPinConfigured: Boolean = false,
    val parentalLoading: Boolean = false,
    val parentalLoadError: String? = null,
    val parentalSessionUnlocked: Boolean = false,
    val pinDialogVisible: Boolean = false,
    val pendingParentalChannel: LiveChannel? = null,
    val pinVerificationLoading: Boolean = false,
    val pinVerificationError: String? = null
) {
    val favoritesError: String?
        get() = favoriteMutationError ?: favoritesLoadError

    val refreshing: Boolean
        get() = categoriesRefreshing || channelsRefreshing
}
