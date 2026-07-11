package com.example.ui.feature.live

import com.example.data.LiveChannel

internal object LiveParentalPresentationPolicy {

    fun categoryShowsLock(
        parentalControlsEnabled: Boolean,
        categoryId: String,
        lockedCategoryIds: Set<String>
    ): Boolean {
        return parentalControlsEnabled && categoryId in lockedCategoryIds
    }

    fun channelShowsLock(
        parentalControlsEnabled: Boolean,
        channel: LiveChannel,
        lockedCategoryIds: Set<String>
    ): Boolean {
        return parentalControlsEnabled &&
            (channel.isAdult || channel.isLocked || channel.categoryId in lockedCategoryIds)
    }
}
