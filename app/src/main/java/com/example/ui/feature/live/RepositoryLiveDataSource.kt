package com.example.ui.feature.live

import com.example.data.Category
import com.example.data.LiveChannel
import com.example.data.IptvRepository
import kotlinx.coroutines.flow.Flow

class RepositoryLiveDataSource(
    private val repository: IptvRepository
) : LiveDataSource {

    override fun observeVisibleCategories(
        providerId: String
    ): Flow<List<Category>> {
        return repository.observeVisibleCategories("LIVE")
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
