package com.example.ui.feature.live

internal object LiveParentalPolicySerializer {

    fun serialize(
        lockedCategoryIds: Collection<String>,
        hideAdultContent: Boolean
    ): String {
        val normalizedIds = lockedCategoryIds
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != HIDE_ADULT_CONTENT_MARKER }
            .distinct()
            .sorted()

        val tokens = if (hideAdultContent) {
            normalizedIds + HIDE_ADULT_CONTENT_MARKER
        } else {
            normalizedIds
        }

        return tokens.joinToString(",")
    }
}
