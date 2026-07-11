package com.example.ui.feature.live

import com.example.data.Category

data class LiveCategoryVisibilitySnapshot(
    val totalCategoryCount: Int,
    val visibleCategories: List<Category>
) {
    val isAuthoritative: Boolean
        get() = totalCategoryCount > 0
}
