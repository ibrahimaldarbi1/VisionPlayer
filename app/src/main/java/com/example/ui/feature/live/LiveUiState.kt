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
    val categoryVisibilityReady: Boolean = false
) {
    val refreshing: Boolean
        get() = categoriesRefreshing || channelsRefreshing
}
