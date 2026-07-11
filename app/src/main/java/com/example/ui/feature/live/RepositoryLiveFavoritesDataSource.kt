package com.example.ui.feature.live

import com.example.data.FavoriteEntity
import com.example.data.IptvRepository
import com.example.data.LiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RepositoryLiveFavoritesDataSource(
    private val repository: IptvRepository
) : LiveFavoritesDataSource {

    override fun observeLiveFavorites(
        providerId: String
    ): Flow<List<FavoriteEntity>> {
        return repository
            .favorites
            .map { favorites ->
                favorites.filter {
                    it.contentType == LIVE_CONTENT_TYPE
                }
            }
            .distinctUntilChanged()
    }

    override suspend fun addLiveFavorite(
        channel: LiveChannel
    ) {
        // NOTE: The streamUrl persistence is temporary and will be removed
        // during the upcoming secure-storage migration.
        repository.addFavorite(
            FavoriteEntity(
                contentId = channel.id,
                contentType = LIVE_CONTENT_TYPE,
                title = channel.name,
                posterOrLogo = channel.logoUrl,
                streamUrl = channel.streamUrl,
                categoryId = channel.categoryId,
                categoryName = channel.categoryName
            )
        )
    }

    override suspend fun removeLiveFavorite(
        channelId: String
    ) {
        repository.removeFavorite(
            channelId,
            LIVE_CONTENT_TYPE
        )
    }

    companion object {
        private const val LIVE_CONTENT_TYPE = "LIVE"
    }
}
