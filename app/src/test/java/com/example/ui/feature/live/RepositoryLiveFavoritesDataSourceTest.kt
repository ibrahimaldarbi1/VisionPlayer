package com.example.ui.feature.live

import com.example.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepositoryLiveFavoritesDataSourceTest {

    class FakeIptvDao : IptvDao {
        val favoritesFlow = MutableSharedFlow<List<FavoriteEntity>>(replay = 1)
        val addedFavorites = mutableListOf<FavoriteEntity>()
        val removedFavorites = mutableListOf<Pair<String, String>>()

        override fun getAllFavorites(): Flow<List<FavoriteEntity>> = favoritesFlow

        override suspend fun addFavorite(favorite: FavoriteEntity) {
            addedFavorites.add(favorite)
        }

        override suspend fun removeFavorite(contentId: String, type: String) {
            removedFavorites.add(Pair(contentId, type))
        }

        override fun getSessionFlow(): Flow<SessionEntity?> = flow { emit(null) }
        override suspend fun getSessionDirect(): SessionEntity? = null
        override suspend fun insertSession(session: SessionEntity) {}
        override suspend fun clearSession() {}
        override fun getFavoritesByType(type: String): Flow<List<FavoriteEntity>> = flow { emit(emptyList()) }
        override fun isFavorite(contentId: String, type: String): Flow<Boolean> = flow { emit(false) }
        override fun getContinueWatchingFlow(): Flow<List<ContinueWatchingEntity>> = flow { emit(emptyList()) }
        override suspend fun saveContinueWatching(progress: ContinueWatchingEntity) {}
        override suspend fun removeContinueWatching(contentId: String) {}
        override fun getRecentlyWatchedFlow(): Flow<List<RecentlyWatchedEntity>> = flow { emit(emptyList()) }
        override suspend fun saveRecentlyWatched(recent: RecentlyWatchedEntity) {}
        override suspend fun deleteRecent(contentId: String) {}
        override fun getEpgForChannel(channelId: String, now: Long): Flow<List<EpgProgramEntity>> = flow { emit(emptyList()) }
        override suspend fun getCurrentProgram(channelId: String, time: Long): EpgProgramEntity? = null
        override suspend fun getEpgProgramsInWindow(fromTime: Long, toTime: Long): List<EpgProgramEntity> = emptyList()
        
        override suspend fun getEpgProgramsInWindowForChannels(fromTime: Long, toTime: Long, channelIds: List<String>): List<EpgProgramEntity> = emptyList()
        override suspend fun insertEpgPrograms(programs: List<EpgProgramEntity>) {}
        override suspend fun pruneOldEpg(now: Long) {}
        override fun getParentalSettingsFlow(): Flow<ParentalControlEntity?> = flow { emit(null) }
        override suspend fun getParentalSettingsDirect(): ParentalControlEntity? = null
        override suspend fun saveParentalSettings(settings: ParentalControlEntity) {}
        override suspend fun clearParentalSettings() {}
        
        override suspend fun upsertCategories(categories: List<CategoryEntity>) {}
        override suspend fun upsertLiveChannels(channels: List<LiveChannelEntity>) {}
        override suspend fun upsertMovies(movies: List<MovieStreamEntity>) {}
        override suspend fun upsertSeries(series: List<SeriesStreamEntity>) {}
        override suspend fun upsertSeasons(seasons: List<SeriesSeasonEntity>) {}
        override suspend fun upsertEpisodes(episodes: List<SeriesEpisodeEntity>) {}
        
        override fun observeCategories(type: String): Flow<List<CategoryEntity>> = flow { emit(emptyList()) }
        override fun observeVisibleCategories(type: String): Flow<List<CategoryEntity>> = flow { emit(emptyList()) }
        override fun observeAllCategoriesForManagement(type: String): Flow<List<CategoryEntity>> = flow { emit(emptyList()) }
        
        override suspend fun setCategoryHidden(type: String, categoryId: String, hidden: Boolean) {}
        override suspend fun setCategoryPinned(type: String, categoryId: String, pinned: Boolean) {}
        override suspend fun updateCategorySortOrderSingle(type: String, categoryId: String, sortOrder: Int) {}
        
        override fun observeLiveChannels(categoryId: String?): Flow<List<LiveChannelEntity>> = flow { emit(emptyList()) }
        override fun observeMovies(categoryId: String?, limit: Int, offset: Int): Flow<List<MovieStreamEntity>> = flow { emit(emptyList()) }
        override fun observeSeries(categoryId: String?, limit: Int, offset: Int): Flow<List<SeriesStreamEntity>> = flow { emit(emptyList()) }
        override fun observeSeasons(seriesId: String): Flow<List<SeriesSeasonEntity>> = flow { emit(emptyList()) }
        override fun observeEpisodes(seriesId: String, seasonNumber: Int): Flow<List<SeriesEpisodeEntity>> = flow { emit(emptyList()) }
        
        override suspend fun searchLiveChannels(query: String, limit: Int): List<LiveChannelEntity> = emptyList()
        override suspend fun searchMovies(query: String, limit: Int): List<MovieStreamEntity> = emptyList()
        override suspend fun searchSeries(query: String, limit: Int): List<SeriesStreamEntity> = emptyList()
        
        override suspend fun findBestMovieMatch(normalizedTitle: String): MovieStreamEntity? = null
        override suspend fun findMovieMatchesLike(query: String, limit: Int): List<MovieStreamEntity> = emptyList()
        override suspend fun findBestSeriesMatch(normalizedTitle: String): SeriesStreamEntity? = null
        override suspend fun findSeriesMatchesLike(query: String, limit: Int): List<SeriesStreamEntity> = emptyList()
        
        override suspend fun clearCategoriesByType(type: String) {}
        override suspend fun clearLiveChannels(categoryId: String?) {}
        override suspend fun clearMovies(categoryId: String?) {}
        override suspend fun clearSeries(categoryId: String?) {}
        override suspend fun clearSeasons(seriesId: String) {}
        override suspend fun clearEpisodes(seriesId: String) {}
        
        override suspend fun getMaxCategoryUpdatedAt(type: String): Long? = null
        override suspend fun getMaxLiveChannelUpdatedAt(categoryId: String): Long? = null
        override suspend fun getMaxLiveChannelUpdatedAtGlobal(): Long? = null
        override suspend fun getMaxMovieUpdatedAt(categoryId: String): Long? = null
        override suspend fun getMaxSeriesUpdatedAt(categoryId: String): Long? = null
        override suspend fun getMaxSeasonUpdatedAt(seriesId: String): Long? = null
        override suspend fun getMaxEpisodeUpdatedAt(seriesId: String): Long? = null
        
        override suspend fun getCachedLiveChannelsSnapshot(): List<LiveChannelEntity> = emptyList()
        override suspend fun getCachedBeinChannelsSnapshot(): List<LiveChannelEntity> = emptyList()
    }

    @Test
    fun testObserveLiveFavoritesFiltersToLive() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveFavoritesDataSource(repository)

        val favoritesList = listOf(
            FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1"),
            FavoriteEntity("movie1", "MOVIE", "Movie 1", "logo", "url", "cat1", "Category 1")
        )
        dao.favoritesFlow.emit(favoritesList)

        var emittedList: List<FavoriteEntity>? = null
        val job = launch {
            dataSource.observeLiveFavorites("prov_1").collect {
                emittedList = it
            }
        }
        
        advanceUntilIdle()

        assertNotNull(emittedList)
        assertEquals(1, emittedList!!.size)
        assertEquals("chan1", emittedList!![0].contentId)
        job.cancel()
    }

    @Test
    fun testObserveLiveFavoritesEmitsDistinctChanges() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveFavoritesDataSource(repository)

        var emissionCount = 0
        val job = launch {
            dataSource.observeLiveFavorites("prov_1").collect {
                emissionCount++
            }
        }
        
        val list1 = listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1"))
        val list2 = listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1"))
        
        dao.favoritesFlow.emit(list1)
        advanceUntilIdle()
        
        dao.favoritesFlow.emit(list2)
        advanceUntilIdle()

        assertEquals(1, emissionCount)
        job.cancel()
    }

    @Test
    fun testAddLiveFavoriteDelegation() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveFavoritesDataSource(repository)

        val channel = LiveChannel(
            id = "chan1",
            name = "Channel 1",
            streamUrl = "url",
            logoUrl = "logo",
            categoryId = "cat1",
            categoryName = "Category 1",
            epgId = "epg_1",
            channelNumber = 1
        )

        dataSource.addLiveFavorite(channel)

        assertEquals(1, dao.addedFavorites.size)
        val favorite = dao.addedFavorites[0]
        assertEquals("chan1", favorite.contentId)
        assertEquals("LIVE", favorite.contentType)
        assertEquals("Channel 1", favorite.title)
        assertEquals("logo", favorite.posterOrLogo)
        assertEquals("url", favorite.streamUrl)
        assertEquals("cat1", favorite.categoryId)
        assertEquals("Category 1", favorite.categoryName)
    }

    @Test
    fun testRemoveLiveFavoriteDelegation() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveFavoritesDataSource(repository)

        dataSource.removeLiveFavorite("chan1")

        assertEquals(1, dao.removedFavorites.size)
        assertEquals("chan1", dao.removedFavorites[0].first)
        assertEquals("LIVE", dao.removedFavorites[0].second)
    }
}
