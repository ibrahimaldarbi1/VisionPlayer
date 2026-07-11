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
class RepositoryLiveParentalDataSourceTest {

    class FakeIptvDao : IptvDao {
        val parentalFlow = MutableSharedFlow<ParentalControlEntity?>(replay = 1)
        var directParentalSettings: ParentalControlEntity? = null

        override fun getParentalSettingsFlow(): Flow<ParentalControlEntity?> = parentalFlow
        override suspend fun getParentalSettingsDirect(): ParentalControlEntity? = directParentalSettings

        override fun getAllFavorites(): Flow<List<FavoriteEntity>> = flow { emit(emptyList()) }
        override suspend fun addFavorite(favorite: FavoriteEntity) {}
        override suspend fun removeFavorite(contentId: String, type: String) {}
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
    fun testObserveStatus_emitsConfiguredTrueWhenPinIsNotBlank() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveParentalDataSource(repository)

        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234"))

        var emittedStatus: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect {
                emittedStatus = it
            }
        }
        advanceUntilIdle()

        assertNotNull(emittedStatus)
        assertTrue(emittedStatus!!.pinConfigured)
        job.cancel()
    }

    @Test
    fun testObserveStatus_emitsConfiguredFalseWhenPinIsBlank() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveParentalDataSource(repository)

        dao.parentalFlow.emit(ParentalControlEntity(pin = ""))

        var emittedStatus: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect {
                emittedStatus = it
            }
        }
        advanceUntilIdle()

        assertNotNull(emittedStatus)
        assertFalse(emittedStatus!!.pinConfigured)
        job.cancel()
    }

    @Test
    fun testObserveStatus_emitsConfiguredFalseWhenSettingsNull() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveParentalDataSource(repository)

        dao.parentalFlow.emit(null)

        var emittedStatus: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect {
                emittedStatus = it
            }
        }
        advanceUntilIdle()

        assertNotNull(emittedStatus)
        assertFalse(emittedStatus!!.pinConfigured)
        job.cancel()
    }

    @Test
    fun testVerifyPin_returnsTrueOnCorrectPin() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveParentalDataSource(repository)

        dao.directParentalSettings = ParentalControlEntity(pin = "1234")

        val result = dataSource.verifyPin("prov_1", "1234")
        assertTrue(result)
    }

    @Test
    fun testVerifyPin_returnsFalseOnIncorrectPin() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveParentalDataSource(repository)

        dao.directParentalSettings = ParentalControlEntity(pin = "1234")

        val result = dataSource.verifyPin("prov_1", "9999")
        assertFalse(result)
    }

    @Test
    fun testVerifyPin_returnsFalseOnNullSettings() = runTest {
        val dao = FakeIptvDao()
        val context = RuntimeEnvironment.getApplication()
        val repository = IptvRepository(dao, context)
        val dataSource = RepositoryLiveParentalDataSource(repository)

        dao.directParentalSettings = null

        val result = dataSource.verifyPin("prov_1", "1234")
        assertFalse(result)
    }

    @Test
    fun observeStatus_emitsLockedCategoryIds() = runTest {
        val dao = FakeIptvDao()
        val repository = IptvRepository(dao, RuntimeEnvironment.getApplication())
        val dataSource = RepositoryLiveParentalDataSource(repository)
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = "cat_1,cat_2"))

        var emitted: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect { emitted = it }
        }
        advanceUntilIdle()
        assertEquals(setOf("cat_1", "cat_2"), emitted?.lockedCategoryIds)
        job.cancel()
    }

    @Test
    fun observeStatus_exactMarkerEnablesHideMode() = runTest {
        val dao = FakeIptvDao()
        val repository = IptvRepository(dao, RuntimeEnvironment.getApplication())
        val dataSource = RepositoryLiveParentalDataSource(repository)
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = "__HIDDEN__"))

        var emitted: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect { emitted = it }
        }
        advanceUntilIdle()
        assertTrue(emitted!!.hideAdultContent)
        job.cancel()
    }

    @Test
    fun observeStatus_markerIsExcludedFromLockedIds() = runTest {
        val dao = FakeIptvDao()
        val repository = IptvRepository(dao, RuntimeEnvironment.getApplication())
        val dataSource = RepositoryLiveParentalDataSource(repository)
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = "cat_1,__HIDDEN__"))

        var emitted: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect { emitted = it }
        }
        advanceUntilIdle()
        assertTrue(emitted!!.hideAdultContent)
        assertEquals(setOf("cat_1"), emitted?.lockedCategoryIds)
        job.cancel()
    }

    @Test
    fun observeStatus_whitespaceAndDuplicateIdsNormalize() = runTest {
        val dao = FakeIptvDao()
        val repository = IptvRepository(dao, RuntimeEnvironment.getApplication())
        val dataSource = RepositoryLiveParentalDataSource(repository)
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = " cat_1 , cat_1, cat_2 "))

        var emitted: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect { emitted = it }
        }
        advanceUntilIdle()
        assertEquals(setOf("cat_1", "cat_2"), emitted?.lockedCategoryIds)
        job.cancel()
    }

    @Test
    fun observeStatus_markerSubstringDoesNotEnableHiding() = runTest {
        val dao = FakeIptvDao()
        val repository = IptvRepository(dao, RuntimeEnvironment.getApplication())
        val dataSource = RepositoryLiveParentalDataSource(repository)
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = "x__HIDDEN__x"))

        var emitted: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect { emitted = it }
        }
        advanceUntilIdle()
        assertFalse(emitted!!.hideAdultContent)
        assertEquals(setOf("x__HIDDEN__x"), emitted?.lockedCategoryIds)
        job.cancel()
    }

    @Test
    fun observeStatus_lowercaseMarkerDoesNotEnableHiding() = runTest {
        val dao = FakeIptvDao()
        val repository = IptvRepository(dao, RuntimeEnvironment.getApplication())
        val dataSource = RepositoryLiveParentalDataSource(repository)
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = "__hidden__"))

        var emitted: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect { emitted = it }
        }
        advanceUntilIdle()
        assertFalse(emitted!!.hideAdultContent)
        assertEquals(setOf("__hidden__"), emitted?.lockedCategoryIds)
        job.cancel()
    }

    @Test
    fun observeStatus_categoryIdCasingIsPreserved() = runTest {
        val dao = FakeIptvDao()
        val repository = IptvRepository(dao, RuntimeEnvironment.getApplication())
        val dataSource = RepositoryLiveParentalDataSource(repository)
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = "cAt_1,CaT_2"))

        var emitted: LiveParentalStatus? = null
        val job = launch {
            dataSource.observeStatus("prov_1").collect { emitted = it }
        }
        advanceUntilIdle()
        assertEquals(setOf("cAt_1", "CaT_2"), emitted?.lockedCategoryIds)
        job.cancel()
    }

    @Test
    fun observeStatus_equivalentNormalizedStatusEmissionsAreSuppressed() = runTest {
        val dao = FakeIptvDao()
        val repository = IptvRepository(dao, RuntimeEnvironment.getApplication())
        val dataSource = RepositoryLiveParentalDataSource(repository)
        
        var emissionCount = 0
        val job = launch {
            dataSource.observeStatus("prov_1").collect { emissionCount++ }
        }
        
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = "cat_1,cat_2"))
        advanceUntilIdle()
        assertEquals(1, emissionCount)
        
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = "cat_2,cat_1"))
        advanceUntilIdle()
        assertEquals(1, emissionCount) // Suppressed due to distinctUntilChanged
        
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = " cat_1 , cat_2 "))
        advanceUntilIdle()
        assertEquals(1, emissionCount) // Suppressed
        
        dao.parentalFlow.emit(ParentalControlEntity(pin = "9999", lockedCategories = "cat_1,cat_2"))
        advanceUntilIdle()
        assertEquals(1, emissionCount) // Suppressed because pinConfigured remains true and categories are the same
        
        dao.parentalFlow.emit(ParentalControlEntity(pin = "1234", lockedCategories = "cat_3"))
        advanceUntilIdle()
        assertEquals(2, emissionCount)
        
        job.cancel()
    }
}
