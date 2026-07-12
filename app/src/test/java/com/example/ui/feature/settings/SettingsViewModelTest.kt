package com.example.ui.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.FeatureConfig
import com.example.config.ProviderProfile
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsViewModelTest {

    class FakeIptvDao : IptvDao {
        override fun getParentalSettingsFlow(): Flow<ParentalControlEntity?> = flow { emit(null) }
        override suspend fun getParentalSettingsDirect(): ParentalControlEntity? = null
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

    class FakeIptvRepository(context: Context) : IptvRepository(FakeIptvDao(), context) {
        var refreshEpgCalls = 0
        var sortOrderCalls = 0
        var shouldDelay = false

        override suspend fun refreshEpg() {
            if (shouldDelay) delay(1000)
            refreshEpgCalls++
        }

        override suspend fun updateCategorySortOrder(type: String, orderedCategoryIds: List<String>) {
            if (shouldDelay) delay(1000)
            sortOrderCalls++
        }

        override fun observeAllCategoriesForManagement(type: String) = flowOf(emptyList<CategoryManagementItem>())
    }

    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeIptvRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        fakeRepository = FakeIptvRepository(context)
        viewModel = SettingsViewModel(fakeRepository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createProfile(
        liveEnabled: Boolean,
        moviesEnabled: Boolean,
        seriesEnabled: Boolean,
        epgEnabled: Boolean
    ): ProviderProfile {
        return ProviderProfile(
            id = "test_profile",
            providerId = "test_provider",
            name = "Test", appName = "Test", backendBaseUrl = "http://test", branding = com.example.config.BrandingConfig(), support = com.example.config.SupportConfig(),
            features = FeatureConfig(
                liveTvEnabled = liveEnabled,
                moviesEnabled = moviesEnabled,
                seriesEnabled = seriesEnabled,
                epgEnabled = epgEnabled
            )
        )
    }

    @Test
    fun testCategoryManagement_refusesToOpenWhenAllDisabled() = runTest {
        val profile = createProfile(false, false, false, false)
        viewModel.onProfileChanged(profile)
        
        viewModel.setSubScreen("CATEGORY_MANAGEMENT")
        assertNull(viewModel.uiState.value.subScreen)
    }

    @Test
    fun testCategoryManagement_opensWhenOneEnabled() = runTest {
        val profile = createProfile(true, false, false, false)
        viewModel.onProfileChanged(profile)
        
        viewModel.setSubScreen("CATEGORY_MANAGEMENT")
        assertEquals("CATEGORY_MANAGEMENT", viewModel.uiState.value.subScreen)
        assertEquals("LIVE", viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testCategoryManagement_rejectsDisabledTab() = runTest {
        val profile = createProfile(true, false, false, false)
        viewModel.onProfileChanged(profile)
        viewModel.setSubScreen("CATEGORY_MANAGEMENT")
        
        viewModel.selectTab("MOVIE")
        // Should remain LIVE
        assertEquals("LIVE", viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testOperationsCancelledOnTabChange() = runTest {
        val profile = createProfile(true, true, false, false)
        viewModel.onProfileChanged(profile)
        viewModel.setSubScreen("CATEGORY_MANAGEMENT")
        viewModel.selectTab("LIVE")
        
        fakeRepository.shouldDelay = true
        viewModel.reorderCategories(listOf("1", "2"))
        
        assertTrue(viewModel.uiState.value.isCategoryOperating)
        
        // Change tab
        viewModel.selectTab("MOVIE")
        
        // Operation should be cancelled
        assertFalse(viewModel.uiState.value.isCategoryOperating)
        advanceUntilIdle()
        assertEquals(0, fakeRepository.sortOrderCalls)
    }

    @Test
    fun testOperationsCancelledOnProfileChange() = runTest {
        val profile = createProfile(true, true, false, false)
        viewModel.onProfileChanged(profile)
        viewModel.setSubScreen("CATEGORY_MANAGEMENT")
        
        fakeRepository.shouldDelay = true
        viewModel.reorderCategories(listOf("1", "2"))
        assertTrue(viewModel.uiState.value.isCategoryOperating)
        
        // Change profile
        val newProfile = createProfile(false, true, false, false)
        viewModel.onProfileChanged(newProfile)
        
        assertFalse(viewModel.uiState.value.isCategoryOperating)
        advanceUntilIdle()
        assertEquals(0, fakeRepository.sortOrderCalls)
    }

    @Test
    fun testOperationsCancelledOnSubScreenClose() = runTest {
        val profile = createProfile(true, true, false, false)
        viewModel.onProfileChanged(profile)
        viewModel.setSubScreen("CATEGORY_MANAGEMENT")
        
        fakeRepository.shouldDelay = true
        viewModel.reorderCategories(listOf("1", "2"))
        assertTrue(viewModel.uiState.value.isCategoryOperating)
        
        // Close subscreen
        viewModel.setSubScreen(null)
        
        assertFalse(viewModel.uiState.value.isCategoryOperating)
        advanceUntilIdle()
        assertEquals(0, fakeRepository.sortOrderCalls)
    }

    @Test
    fun testObsoleteOperationDoesNotUpdateProgress() = runTest {
        val profile = createProfile(true, true, false, false)
        viewModel.onProfileChanged(profile)
        viewModel.setSubScreen("CATEGORY_MANAGEMENT")
        
        fakeRepository.shouldDelay = true
        viewModel.reorderCategories(listOf("1", "2"))
        
        // Close subscreen, cancelling the op
        viewModel.setSubScreen(null)
        assertFalse(viewModel.uiState.value.isCategoryOperating)
        
        // Complete the delay just in case it wasn't cancelled (it should be)
        advanceUntilIdle()
        
        // Progress should still be false
        assertFalse(viewModel.uiState.value.isCategoryOperating)
    }

    @Test
    fun testEpgRefresh_notStartingWhenDisabled() = runTest {
        val profile = createProfile(liveEnabled = true, moviesEnabled = false, seriesEnabled = false, epgEnabled = false)
        viewModel.onProfileChanged(profile)
        
        viewModel.refreshCache()
        assertFalse(viewModel.uiState.value.isRefreshingCache)
        assertEquals(0, fakeRepository.refreshEpgCalls)
    }

    @Test
    fun testEpgRefresh_invalidatedOnFeatureChange() = runTest {
        val profile = createProfile(liveEnabled = true, moviesEnabled = false, seriesEnabled = false, epgEnabled = true)
        viewModel.onProfileChanged(profile)
        
        fakeRepository.shouldDelay = true
        viewModel.refreshCache()
        assertTrue(viewModel.uiState.value.isRefreshingCache)
        
        // Change profile feature (disable EPG)
        val newProfile = createProfile(liveEnabled = true, moviesEnabled = false, seriesEnabled = false, epgEnabled = false)
        viewModel.onProfileChanged(newProfile)
        
        assertFalse(viewModel.uiState.value.isRefreshingCache)
        advanceUntilIdle()
        // It shouldn't complete if it was cancelled
        assertEquals(0, fakeRepository.refreshEpgCalls)
    }
    @Test
    fun testObsoleteEpgRefreshDoesNotChangeLoadingState() = runTest {
        val profile = createProfile(liveEnabled = true, moviesEnabled = false, seriesEnabled = false, epgEnabled = true)
        viewModel.onProfileChanged(profile)
        
        fakeRepository.shouldDelay = true
        viewModel.refreshCache()
        assertTrue(viewModel.uiState.value.isRefreshingCache)
        
        // Change profile feature (disable EPG), which cancels the first one
        val newProfile = createProfile(liveEnabled = true, moviesEnabled = false, seriesEnabled = false, epgEnabled = false)
        viewModel.onProfileChanged(newProfile)
        
        assertFalse(viewModel.uiState.value.isRefreshingCache)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isRefreshingCache)
    }
}
