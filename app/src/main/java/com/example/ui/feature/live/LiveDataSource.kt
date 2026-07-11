package com.example.ui.feature.live

import com.example.data.Category
import com.example.data.LiveChannel
import kotlinx.coroutines.flow.Flow

interface LiveDataSource {

    fun observeCategoryVisibility(
        providerId: String
    ): Flow<LiveCategoryVisibilitySnapshot>

    fun loadCategories(
        providerId: String
    ): Flow<List<Category>>

    fun loadChannels(
        providerId: String,
        categoryId: String?
    ): Flow<List<LiveChannel>>
}
