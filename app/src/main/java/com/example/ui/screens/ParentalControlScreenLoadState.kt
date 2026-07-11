package com.example.ui.screens

import com.example.ui.feature.live.LiveParentalStatus

internal data class ParentalControlScreenLoadState(
    val isPinSet: Boolean,
    val isUnlocked: Boolean,
    val hideAdultContent: Boolean,
    val lockedCategoryIds: Set<String>
)

internal object ParentalControlScreenStateMapper {

    fun fromStatus(status: LiveParentalStatus?): ParentalControlScreenLoadState {
        if (status == null) {
            return ParentalControlScreenLoadState(
                isPinSet = false,
                isUnlocked = true,
                hideAdultContent = false,
                lockedCategoryIds = emptySet()
            )
        }

        return ParentalControlScreenLoadState(
            isPinSet = status.pinConfigured,
            isUnlocked = !status.pinConfigured,
            hideAdultContent = status.hideAdultContent,
            lockedCategoryIds = status.lockedCategoryIds
        )
    }
}
