package com.example.ui.feature.live

import com.example.data.Category
import com.example.data.LiveChannel
import com.example.data.IptvRepository
import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

class RepositoryLiveDataSource(
    private val repository: IptvRepository
) : LiveDataSource {

    override fun observeCategoryVisibility(
        providerId: String
    ): Flow<LiveCategoryVisibilitySnapshot> {
        return repository
            .observeAllCategoriesForManagement("LIVE")
            .map { allCategories ->
                LiveCategoryVisibilitySnapshot(
                    totalCategoryCount = allCategories.size,
                    visibleCategories = allCategories
                        .filter { !it.hidden }
                        .map {
                            Category(
                                id = it.id,
                                name = it.name,
                                type = it.type
                            )
                        }
                )
            }
            .distinctUntilChanged()
    }

    override fun loadCategories(
        providerId: String
    ): Flow<List<Category>> {
        return repository.getCategories("LIVE")
    }

    override fun loadChannels(
        providerId: String,
        categoryId: String?
    ): Flow<List<LiveChannel>> {
        return repository.getLiveChannels(categoryId)
    }
}
