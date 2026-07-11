package com.example.ui.feature.live

data class LiveParentalStatus(
    val pinConfigured: Boolean,
    val lockedCategoryIds: Set<String> = emptySet(),
    val hideAdultContent: Boolean = false
)
