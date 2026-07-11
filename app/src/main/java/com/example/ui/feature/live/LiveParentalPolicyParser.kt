package com.example.ui.feature.live

internal const val HIDE_ADULT_CONTENT_MARKER = "__HIDDEN__"

internal object LiveParentalPolicyParser {

    fun parse(
        storedPin: String?,
        storedCategoryPolicy: String?
    ): LiveParentalStatus {
        val tokens = storedCategoryPolicy
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val hideAdultContent = tokens.any { it == HIDE_ADULT_CONTENT_MARKER }

        val lockedCategoryIds = tokens
            .filter { it != HIDE_ADULT_CONTENT_MARKER }
            .toSet()

        return LiveParentalStatus(
            pinConfigured = !storedPin.isNullOrBlank(),
            lockedCategoryIds = lockedCategoryIds,
            hideAdultContent = hideAdultContent
        )
    }
}
