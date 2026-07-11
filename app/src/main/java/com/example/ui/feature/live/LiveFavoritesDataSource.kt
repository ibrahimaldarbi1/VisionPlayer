package com.example.ui.feature.live

import com.example.data.FavoriteEntity
import com.example.data.LiveChannel
import kotlinx.coroutines.flow.Flow

interface LiveFavoritesDataSource {

    fun observeLiveFavorites(
        providerId: String
    ): Flow<List<FavoriteEntity>>

    suspend fun addLiveFavorite(
        channel: LiveChannel
    )

    suspend fun removeLiveFavorite(
        channelId: String
    )
}
