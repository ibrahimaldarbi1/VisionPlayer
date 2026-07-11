package com.example

import com.example.config.ProviderProfile
import com.example.data.Category
import com.example.data.LiveChannel
import com.example.ui.feature.live.LiveDataSource
import com.example.ui.feature.live.LiveViewModel
import com.example.ui.feature.live.LiveCategoryVisibilitySnapshot
import com.example.ui.feature.live.LiveFavoritesDataSource
import com.example.data.FavoriteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeFavorites = FakeLiveFavoritesDataSource()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Reset fakeFavorites for each test
        fakeFavorites.observeFavoritesCallCount = 0
        fakeFavorites.addFavoriteCallCount = 0
        fakeFavorites.removeFavoriteCallCount = 0
        fakeFavorites.observeFavoritesDelayMs = 0L
        fakeFavorites.addFavoriteDelayMs = 0L
        fakeFavorites.removeFavoriteDelayMs = 0L
        fakeFavorites.observeFavoritesError = null
        fakeFavorites.addFavoriteError = null
        fakeFavorites.removeFavoriteError = null
        fakeFavorites.lastObserveProviderId = null
        fakeFavorites.lastAddedChannel = null
        fakeFavorites.lastRemovedChannelId = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    class FakeLiveDataSource : LiveDataSource {
        var categoriesEmissions: List<List<Category>> = emptyList()
        var channelsEmissions: List<List<LiveChannel>> = emptyList()
        var observeEmissions: List<List<Category>> = emptyList()
        var snapshotEmissions: List<LiveCategoryVisibilitySnapshot> = emptyList()

        val observeFlow = kotlinx.coroutines.flow.MutableSharedFlow<Any>(replay = 1)

        var categoriesDelayMs: Long = 0L
        var channelsDelayMs: Long = 0L
        var observeDelayMs: Long = 0L

        var categoriesError: Throwable? = null
        var channelsError: Throwable? = null
        var observeError: Throwable? = null

        var categoriesCallCount = 0
        var channelsCallCount = 0
        var observeCallCount = 0

        var lastCategoriesProviderId: String? = null
        var lastChannelsProviderId: String? = null
        var lastChannelsCategoryId: String? = null
        var lastObserveProviderId: String? = null

        val categoryResultsByProvider = mutableMapOf<String, List<List<Category>>>()
        val channelResultsByRequest = mutableMapOf<Pair<String, String?>, List<List<LiveChannel>>>()
        val categoryDelayByProvider = mutableMapOf<String, Long>()
        val channelDelayByRequest = mutableMapOf<Pair<String, String?>, Long>()
        val observeResultsByProvider = mutableMapOf<String, List<List<Category>>>()
        val visibilitySnapshotsByProvider = mutableMapOf<String, List<LiveCategoryVisibilitySnapshot>>()
        val observeDelayByProvider = mutableMapOf<String, Long>()

        override fun observeCategoryVisibility(providerId: String): Flow<LiveCategoryVisibilitySnapshot> = flow {
            observeCallCount++
            lastObserveProviderId = providerId
            val delayMs = observeDelayByProvider[providerId] ?: observeDelayMs
            if (delayMs > 0) {
                delay(delayMs)
            }
            observeError?.let { throw it }
            val snapEmissions = visibilitySnapshotsByProvider[providerId] ?: snapshotEmissions
            if (snapEmissions.isNotEmpty()) {
                for (em in snapEmissions) {
                    emit(em)
                }
            } else {
                val emissions = observeResultsByProvider[providerId] ?: observeEmissions
                if (emissions.isNotEmpty()) {
                    for (em in emissions) {
                        emit(LiveCategoryVisibilitySnapshot(
                            totalCategoryCount = em.size,
                            visibleCategories = em
                        ))
                    }
                } else {
                    observeFlow.collect { item ->
                        when (item) {
                            is LiveCategoryVisibilitySnapshot -> emit(item)
                            is List<*> -> emit(LiveCategoryVisibilitySnapshot(
                                totalCategoryCount = item.size,
                                visibleCategories = item.filterIsInstance<Category>()
                            ))
                        }
                    }
                }
            }
        }

        override fun loadCategories(providerId: String): Flow<List<Category>> = flow {
            categoriesCallCount++
            lastCategoriesProviderId = providerId
            val delayMs = categoryDelayByProvider[providerId] ?: categoriesDelayMs
            if (delayMs > 0) {
                delay(delayMs)
            }
            categoriesError?.let { throw it }
            val emissions = categoryResultsByProvider[providerId] ?: categoriesEmissions
            if (emissions.isNotEmpty()) {
                for (em in emissions) {
                    emit(em)
                }
            }
        }

        override fun loadChannels(providerId: String, categoryId: String?): Flow<List<LiveChannel>> = flow {
            channelsCallCount++
            lastChannelsProviderId = providerId
            lastChannelsCategoryId = categoryId
            val key = Pair(providerId, categoryId)
            val delayMs = channelDelayByRequest[key] ?: channelsDelayMs
            if (delayMs > 0) {
                delay(delayMs)
            }
            channelsError?.let { throw it }
            val emissions = channelResultsByRequest[key] ?: channelsEmissions
            if (emissions.isNotEmpty()) {
                for (em in emissions) {
                    emit(em)
                }
            }
        }
    }

    class FakeLiveFavoritesDataSource : LiveFavoritesDataSource {
        var observeFavoritesCallCount = 0
        var addFavoriteCallCount = 0
        var removeFavoriteCallCount = 0

        var observeFavoritesDelayMs: Long = 0L
        var addFavoriteDelayMs: Long = 0L
        var removeFavoriteDelayMs: Long = 0L

        var observeFavoritesError: Throwable? = null
        var addFavoriteError: Throwable? = null
        var removeFavoriteError: Throwable? = null

        val observeFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<FavoriteEntity>>(replay = 1)
        var lastObserveProviderId: String? = null
        var lastAddedChannel: LiveChannel? = null
        var lastRemovedChannelId: String? = null

        override fun observeLiveFavorites(providerId: String): Flow<List<FavoriteEntity>> = flow {
            observeFavoritesCallCount++
            lastObserveProviderId = providerId
            if (observeFavoritesDelayMs > 0) {
                delay(observeFavoritesDelayMs)
            }
            observeFavoritesError?.let { throw it }
            observeFlow.collect { 
                observeFavoritesError?.let { throw it }
                emit(it) 
            }
        }

        override suspend fun addLiveFavorite(channel: LiveChannel) {
            addFavoriteCallCount++
            lastAddedChannel = channel
            if (addFavoriteDelayMs > 0) {
                delay(addFavoriteDelayMs)
            }
            addFavoriteError?.let { throw it }
        }

        override suspend fun removeLiveFavorite(channelId: String) {
            removeFavoriteCallCount++
            lastRemovedChannelId = channelId
            if (removeFavoriteDelayMs > 0) {
                delay(removeFavoriteDelayMs)
            }
            removeFavoriteError?.let { throw it }
        }
    }

    private fun createCategory(id: String, name: String, type: String = "LIVE"): Category {
        return Category(id = id, name = name, type = type)
    }

    private fun createChannel(id: String, name: String, categoryId: String): LiveChannel {
        return LiveChannel(
            id = id,
            name = name,
            streamUrl = "http://example.com/$id",
            logoUrl = "http://example.com/logo/$id",
            categoryId = categoryId,
            categoryName = "Category $categoryId",
            epgId = "epg_$id",
            channelNumber = 1
        )
    }

    private fun createEnabledProfile(providerId: String): ProviderProfile {
        return ProviderProfile(
            id = "profile_$providerId",
            name = "Profile $providerId",
            appName = "App $providerId",
            providerId = providerId,
            features = com.example.config.FeatureConfig(
                liveTvEnabled = true,
                moviesEnabled = false,
                seriesEnabled = false,
                epgEnabled = false,
                searchEnabled = false,
                favoritesEnabled = false,
                parentalControlEnabled = false,
                multiViewEnabled = false,
                footballScheduleEnabled = false
            ),
            branding = com.example.config.BrandingConfig(
                primaryColor = 0xFF123456
            ),
            support = com.example.config.SupportConfig()
        )
    }

    private fun createDisabledProfile(providerId: String): ProviderProfile {
        return ProviderProfile(
            id = "profile_$providerId",
            name = "Profile $providerId",
            appName = "App $providerId",
            providerId = providerId,
            features = com.example.config.FeatureConfig(
                liveTvEnabled = false,
                moviesEnabled = false,
                seriesEnabled = false,
                epgEnabled = false,
                searchEnabled = false,
                favoritesEnabled = false,
                parentalControlEnabled = false,
                multiViewEnabled = false,
                footballScheduleEnabled = false
            ),
            branding = com.example.config.BrandingConfig(
                primaryColor = 0xFF123456
            ),
            support = com.example.config.SupportConfig()
        )
    }

    // 1. Enabled profile loads categories.
    @Test
    fun testEnabledProfileLoadsCategories() = runTest {
        val fake = FakeLiveDataSource().apply {
            categoriesEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(1, fake.categoriesCallCount)
        assertEquals("prov_1", fake.lastCategoriesProviderId)
    }

    // 2. Enabled profile loads all channels initially.
    @Test
    fun testEnabledProfileLoadsAllChannelsInitially() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(1, fake.channelsCallCount)
        assertEquals("prov_1", fake.lastChannelsProviderId)
        assertNull(fake.lastChannelsCategoryId)
    }

    // 3. Disabled profile makes no request.
    @Test
    fun testDisabledProfileMakesNoRequest() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createDisabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(0, fake.categoriesCallCount)
        assertEquals(0, fake.channelsCallCount)
        assertEquals(0, fake.observeCallCount)
    }

    // 4. Disabled profile clears state.
    @Test
    fun testDisabledProfileClearsState() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.featureEnabled)
        assertFalse(vm.uiState.value.categories.isEmpty())

        vm.onProfileChanged(createDisabledProfile("prov_1"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.featureEnabled)
        assertNull(state.providerId)
        assertTrue(state.categories.isEmpty())
        assertTrue(state.channels.isEmpty())
        assertNull(state.selectedCategoryId)
        assertNull(state.categoriesError)
        assertNull(state.channelsError)
        assertFalse(state.categoriesLoading)
        assertFalse(state.channelsLoading)
    }

    // 5. Provider change cancels old category work.
    @Test
    fun testProviderChangeCancelsOldCategoryWork() = runTest {
        val fake = FakeLiveDataSource().apply {
            categoriesDelayMs = 1000L
            categoriesEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceTimeBy(500L)

        vm.onProfileChanged(createEnabledProfile("prov_2"))
        advanceUntilIdle()

        assertEquals(2, fake.categoriesCallCount)
    }

    // 6. Provider change cancels old channel work.
    @Test
    fun testProviderChangeCancelsOldChannelWork() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsDelayMs = 1000L
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceTimeBy(500L)

        vm.onProfileChanged(createEnabledProfile("prov_2"))
        advanceUntilIdle()

        assertEquals(2, fake.channelsCallCount)
    }

    // 7. Old provider category result cannot overwrite new state.
    @Test
    fun testOldProviderCategoryResultCannotOverwriteNewState() = runTest {
        val fake = FakeLiveDataSource().apply {
            categoryDelayByProvider["prov_old"] = 1000L
            categoryResultsByProvider["prov_old"] = listOf(listOf(createCategory("cat_old", "Category Old")))

            categoryDelayByProvider["prov_new"] = 100L
            categoryResultsByProvider["prov_new"] = listOf(listOf(createCategory("cat_new", "Category New")))

            // Observe visible categories for new provider is ready
            observeResultsByProvider["prov_new"] = listOf(listOf(createCategory("cat_new", "Category New")))
            observeResultsByProvider["prov_old"] = listOf(listOf(createCategory("cat_old", "Category Old")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_old"))
        advanceTimeBy(500L) // prov_old is still loading categories

        vm.onProfileChanged(createEnabledProfile("prov_new"))
        advanceUntilIdle() // let everything complete

        // The UI must contain "cat_new" and must NOT contain "cat_old"
        val finalCategories = vm.uiState.value.categories
        assertTrue(finalCategories.any { it.id == "cat_new" })
        assertFalse(finalCategories.any { it.id == "cat_old" })
    }

    // 8. Old provider channel result cannot overwrite new state.
    @Test
    fun testOldProviderChannelResultCannotOverwriteNewState() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelDelayByRequest[Pair("prov_old", null)] = 1000L
            channelResultsByRequest[Pair("prov_old", null)] = listOf(listOf(createChannel("chan_old", "Channel Old", "cat1")))

            channelDelayByRequest[Pair("prov_new", null)] = 100L
            channelResultsByRequest[Pair("prov_new", null)] = listOf(listOf(createChannel("chan_new", "Channel New", "cat1")))

            observeResultsByProvider["prov_new"] = listOf(listOf(createCategory("cat1", "Category 1")))
            observeResultsByProvider["prov_old"] = listOf(listOf(createCategory("cat1", "Category 1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_old"))
        advanceTimeBy(500L) // prov_old channels are still loading

        vm.onProfileChanged(createEnabledProfile("prov_new"))
        advanceUntilIdle() // let everything complete

        val finalChannels = vm.uiState.value.channels
        assertTrue(finalChannels.any { it.id == "chan_new" })
        assertFalse(finalChannels.any { it.id == "chan_old" })
    }

    // 9. Cached category emission is displayed.
    @Test
    fun testCachedCategoryEmissionIsDisplayed() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(listOf(createCategory("cat1", "Category 1")), vm.uiState.value.categories)
    }

    // 10. Refreshed category emission replaces cached data.
    @Test
    fun testRefreshedCategoryEmissionReplacesCachedData() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(
                listOf(createCategory("cat1", "Category 1")),
                listOf(createCategory("cat1", "Category 1"), createCategory("cat2", "Category 2"))
            )
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(
            listOf(createCategory("cat1", "Category 1"), createCategory("cat2", "Category 2")),
            vm.uiState.value.categories
        )
    }

    // 11. Cached channel emission is displayed.
    @Test
    fun testCachedChannelEmissionIsDisplayed() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(listOf(createChannel("chan1", "Channel 1", "cat1")), vm.uiState.value.channels)
    }

    // 12. Refreshed channel emission replaces cached data.
    @Test
    fun testRefreshedChannelEmissionReplacesCachedData() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
            channelsEmissions = listOf(
                listOf(createChannel("chan1", "Channel 1", "cat1")),
                listOf(createChannel("chan1", "Channel 1", "cat1"), createChannel("chan2", "Channel 2", "cat1"))
            )
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(
            listOf(createChannel("chan1", "Channel 1", "cat1"), createChannel("chan2", "Channel 2", "cat1")),
            vm.uiState.value.channels
        )
    }

    // 13. Changing category reloads channels only.
    @Test
    fun testChangingCategoryReloadsChannelsOnly() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1"), createCategory("cat2", "Category 2")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        val initialCategoriesCount = fake.categoriesCallCount
        val initialChannelsCount = fake.channelsCallCount

        vm.selectCategory("cat2")
        advanceUntilIdle()

        assertEquals(initialCategoriesCount, fake.categoriesCallCount)
        assertEquals(initialChannelsCount + 1, fake.channelsCallCount)
        assertEquals("cat2", fake.lastChannelsCategoryId)
    }

    // 14. Changing category does not reload categories.
    @Test
    fun testChangingCategoryDoesNotReloadCategories() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1"), createCategory("cat2", "Category 2")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        val initialCategoriesCount = fake.categoriesCallCount
        vm.selectCategory("cat2")
        advanceUntilIdle()

        assertEquals(initialCategoriesCount, fake.categoriesCallCount)
    }

    // 15. Changing category does not trigger Movies or Series work.
    @Test
    fun testChangingCategoryDoesNotTriggerMoviesOrSeriesWork() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        val initialChannelsCount = fake.channelsCallCount
        vm.selectCategory("cat1")
        advanceUntilIdle()

        assertEquals(initialChannelsCount + 1, fake.channelsCallCount)
    }

    // 16. Same-category selection does nothing.
    @Test
    fun testSameCategorySelectionDoesNothing() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        val beforeCallCount = fake.channelsCallCount
        vm.selectCategory(null) // Already null
        advanceUntilIdle()

        assertEquals(beforeCallCount, fake.channelsCallCount)
    }

    // 17. Old category result cannot overwrite the new category.
    @Test
    fun testOldCategoryResultCannotOverwriteNewCategory() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeResultsByProvider["prov_1"] = listOf(listOf(createCategory("cat1", "Category 1"), createCategory("cat2", "Category 2")))

            channelDelayByRequest[Pair("prov_1", "cat1")] = 1000L
            channelResultsByRequest[Pair("prov_1", "cat1")] = listOf(listOf(createChannel("chan_old", "Old Channel", "cat1")))

            channelDelayByRequest[Pair("prov_1", "cat2")] = 100L
            channelResultsByRequest[Pair("prov_1", "cat2")] = listOf(listOf(createChannel("chan_new", "New Channel", "cat2")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle() // loads initial null category channels

        // Select cat1 (slow request)
        vm.selectCategory("cat1")
        advanceTimeBy(500L) // in flight

        // Select cat2 (fast request)
        vm.selectCategory("cat2")
        advanceUntilIdle() // both finish

        assertEquals("cat2", vm.uiState.value.selectedCategoryId)
        val finalChannels = vm.uiState.value.channels
        assertTrue(finalChannels.any { it.id == "chan_new" })
        assertFalse(finalChannels.any { it.id == "chan_old" })
    }

    // 18. Initial loading stops after success.
    @Test
    fun testInitialLoadingStopsAfterSuccess() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsDelayMs = 500L
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceTimeBy(100L)

        assertTrue(vm.uiState.value.initialLoading)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.initialLoading)
    }

    // 19. Initial loading stops after failure.
    @Test
    fun testInitialLoadingStopsAfterFailure() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsDelayMs = 500L
            channelsError = RuntimeException("Error")
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceTimeBy(100L)

        assertTrue(vm.uiState.value.initialLoading)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.initialLoading)
    }

    // 20. Refresh keeps existing channels visible.
    @Test
    fun testRefreshKeepsExistingChannelsVisible() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.channels.size)

        fake.channelsDelayMs = 1000L
        vm.retryChannels()
        advanceTimeBy(500L)

        assertTrue(vm.uiState.value.refreshing)
        assertEquals(1, vm.uiState.value.channels.size) // still visible

        advanceUntilIdle()
        assertFalse(vm.uiState.value.refreshing)
    }

    // 21. Category failure exposes safe category error.
    @Test
    fun testCategoryFailureExposesSafeCategoryError() = runTest {
        val fake = FakeLiveDataSource().apply {
            categoriesError = RuntimeException("Server breakdown")
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals("Could not load Live TV categories.", vm.uiState.value.categoriesError)
    }

    // 22. Channel failure exposes safe channel error.
    @Test
    fun testChannelFailureExposesSafeChannelError() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsError = RuntimeException("Server breakdown")
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals("Could not load Live TV channels.", vm.uiState.value.channelsError)
    }

    // 23. Normal cancellation exposes no error.
    @Test
    fun testNormalCancellationExposesNoError() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsDelayMs = 1000L
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceTimeBy(500L)

        vm.onProfileChanged(createDisabledProfile("prov_1")) // cancels everything
        advanceUntilIdle()

        assertNull(vm.uiState.value.channelsError)
    }

    // 24. Retry categories starts one new request.
    @Test
    fun testRetryCategoriesStartsOneNewRequest() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        val countBefore = fake.categoriesCallCount
        vm.retryCategories()
        advanceUntilIdle()

        assertEquals(countBefore + 1, fake.categoriesCallCount)
    }

    // 25. Retry channels starts one new request.
    @Test
    fun testRetryChannelsStartsOneNewRequest() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        val countBefore = fake.channelsCallCount
        vm.retryChannels()
        advanceUntilIdle()

        assertEquals(countBefore + 1, fake.channelsCallCount)
    }

    // 26. Invisible/hidden categories reset an invalid selected category.
    @Test
    fun testInvisibleCategoriesResetInvalidSelectedCategory() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        // 1. Emit categories containing cat1 and cat2
        fake.observeFlow.emit(listOf(createCategory("cat1", "Category 1"), createCategory("cat2", "Category 2")))
        advanceUntilIdle()

        // 2. Select cat1
        vm.selectCategory("cat1")
        advanceUntilIdle()
        assertEquals("cat1", vm.uiState.value.selectedCategoryId)

        // 3. Emit categories without cat1
        fake.observeFlow.emit(listOf(createCategory("cat2", "Category 2")))
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedCategoryId)
    }

    // 27. ViewModel clear cancels every job.
    @Test
    fun testViewModelClearCancelsEveryJob() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsDelayMs = 1000L
            categoriesDelayMs = 1000L
            observeDelayMs = 1000L
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceTimeBy(500L)

        val onClearedMethod = vm::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)

        advanceUntilIdle()
        // Jobs are cancelled normally, loading ceases without crashes
        assertFalse(vm.uiState.value.channelsLoading)
        assertFalse(vm.uiState.value.categoriesLoading)
    }

    // 28. Channels arrive before categories: channels remain visible before category readiness.
    @Test
    fun testRaceChannelsArriveBeforeCategories() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.categoryVisibilityReady)
        assertEquals(1, vm.uiState.value.channels.size)
        assertEquals("chan1", vm.uiState.value.channels[0].id)
    }

    // 29. Categories arrive later: channels are then filtered correctly.
    @Test
    fun testRaceCategoriesArriveLater() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1"), createChannel("chan2", "Channel 2", "cat2")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.channels.size)

        fake.observeFlow.emit(listOf(createCategory("cat1", "Category 1")))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.categoryVisibilityReady)
        assertEquals(1, vm.uiState.value.channels.size)
        assertEquals("chan1", vm.uiState.value.channels[0].id)
    }

    // 30. Category observer fails but channels succeed: channels remain visible.
    @Test
    fun testCategoryObserverFailsButChannelsSucceed() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeError = RuntimeException("Connection failed")
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.categoryVisibilityReady)
        assertEquals(1, vm.uiState.value.channels.size)
        assertNotNull(vm.uiState.value.categoriesError)
    }

    // 31. All categories are intentionally hidden after readiness: displayed channels become empty.
    @Test
    fun testAllCategoriesHiddenAfterReadiness() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        fake.observeFlow.emit(listOf(createCategory("cat1", "Category 1")))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.categoryVisibilityReady)

        fake.channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        vm.retryChannels()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.channels.size)

        fake.observeFlow.emit(LiveCategoryVisibilitySnapshot(totalCategoryCount = 1, visibleCategories = emptyList()))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.channels.isEmpty())
    }

    // 32. A hidden selected category resets to All Channels.
    @Test
    fun testHiddenSelectedCategoryResetsToAllChannels() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        fake.observeFlow.emit(listOf(createCategory("cat1", "Category 1"), createCategory("cat2", "Category 2")))
        advanceUntilIdle()

        vm.selectCategory("cat1")
        advanceUntilIdle()
        assertEquals("cat1", vm.uiState.value.selectedCategoryId)

        fake.observeFlow.emit(listOf(createCategory("cat2", "Category 2")))
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedCategoryId)
    }

    // 33. Category refresh and channel refresh overlap: finishing one does not clear the other’s refresh state.
    @Test
    fun testCategoryAndChannelRefreshOverlap() = runTest {
        val fake = FakeLiveDataSource().apply {
            categoriesEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        fake.categoriesDelayMs = 1000L
        fake.channelsDelayMs = 500L

        vm.retryCategories()
        vm.retryChannels()

        advanceTimeBy(100L)
        assertTrue(vm.uiState.value.categoriesRefreshing)
        assertTrue(vm.uiState.value.channelsRefreshing)

        advanceTimeBy(500L)
        assertFalse(vm.uiState.value.channelsRefreshing)
        assertTrue(vm.uiState.value.categoriesRefreshing)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.categoriesRefreshing)
    }

    // 34. Forced same-category refresh keeps old channels visible.
    @Test
    fun testForcedSameCategoryRefreshKeepsOldChannelsVisible() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.channels.size)

        fake.channelsDelayMs = 1000L
        vm.loadChannels("prov_1", null, force = true)
        advanceTimeBy(500L)

        assertEquals(1, vm.uiState.value.channels.size)

        advanceUntilIdle()
    }

    // 35. Non-forced same-category reload after completion keeps old channels visible.
    @Test
    fun testNonForcedSameCategoryReloadAfterCompletionKeepsOldChannelsVisible() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.channels.size)

        fake.channelsDelayMs = 1000L
        vm.loadChannels("prov_1", null, force = false)
        advanceTimeBy(500L)

        assertEquals(1, vm.uiState.value.channels.size)

        advanceUntilIdle()
    }

    // 36. Different-category request clears old category channels.
    @Test
    fun testDifferentCategoryRequestClearsOldCategoryChannels() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1"), createCategory("cat2", "Category 2")))
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.channels.size)

        fake.channelsDelayMs = 1000L
        vm.selectCategory("cat2")
        advanceTimeBy(100L)

        assertTrue(vm.uiState.value.channels.isEmpty())

        advanceUntilIdle()
    }

    // 37. Provider change resets category visibility readiness.
    @Test
    fun testProviderChangeResetsCategoryVisibilityReadiness() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        fake.observeFlow.emit(listOf(createCategory("cat1", "Category 1")))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.categoryVisibilityReady)

        vm.onProfileChanged(createEnabledProfile("prov_2"))

        assertFalse(vm.uiState.value.categoryVisibilityReady)
    }

    // 38. Initial empty Room snapshot: categoryVisibilityReady is false, channel remains visible, no false empty state
    @Test
    fun testInitialEmptyRoomSnapshot() = runTest {
        val fake = FakeLiveDataSource().apply {
            snapshotEmissions = listOf(
                LiveCategoryVisibilitySnapshot(totalCategoryCount = 0, visibleCategories = emptyList())
            )
            channelsEmissions = listOf(
                listOf(createChannel("chan1", "Channel 1", "cat1"))
            )
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.categoryVisibilityReady)
        assertEquals(1, vm.uiState.value.channels.size)
        assertEquals("chan1", vm.uiState.value.channels[0].id)
    }

    // 39. Category request failure with empty Room: channels remain visible, category error is set, readiness remains false
    @Test
    fun testCategoryRequestFailureWithEmptyRoom() = runTest {
        val fake = FakeLiveDataSource().apply {
            snapshotEmissions = listOf(
                LiveCategoryVisibilitySnapshot(totalCategoryCount = 0, visibleCategories = emptyList())
            )
            categoriesError = RuntimeException("Category load failed")
            channelsEmissions = listOf(
                listOf(createChannel("chan1", "Channel 1", "cat1"))
            )
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.categoryVisibilityReady)
        assertEquals(1, vm.uiState.value.channels.size)
        assertNotNull(vm.uiState.value.categoriesError)
    }

    // 40. Categories inserted later: total count goes from 0 to 2, and then filtering becomes active
    @Test
    fun testCategoriesInsertedLater() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsEmissions = listOf(
                listOf(createChannel("chan1", "Channel 1", "cat1"), createChannel("chan2", "Channel 2", "cat2"))
            )
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        // 1. Initially empty snapshot is emitted
        fake.observeFlow.emit(LiveCategoryVisibilitySnapshot(totalCategoryCount = 0, visibleCategories = emptyList()))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.categoryVisibilityReady)
        assertEquals(2, vm.uiState.value.channels.size) // unfiltered

        // 2. Later, authoritative snapshot arrives
        fake.observeFlow.emit(LiveCategoryVisibilitySnapshot(
            totalCategoryCount = 2,
            visibleCategories = listOf(createCategory("cat1", "Category 1"))
        ))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.categoryVisibilityReady)
        assertEquals(1, vm.uiState.value.channels.size)
        assertEquals("chan1", vm.uiState.value.channels[0].id)
    }

    // 41. All categories deliberately hidden: authoritative with 0 visible categories produces 0 channels
    @Test
    fun testAllCategoriesDeliberatelyHidden() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsEmissions = listOf(
                listOf(createChannel("chan1", "Channel 1", "cat1"))
            )
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        // Authoritative but 0 visible categories (all hidden)
        fake.observeFlow.emit(LiveCategoryVisibilitySnapshot(totalCategoryCount = 2, visibleCategories = emptyList()))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.categoryVisibilityReady)
        assertTrue(vm.uiState.value.channels.isEmpty())
    }

    // 42. Initial empty snapshot does not clear an error
    @Test
    fun testInitialEmptySnapshotDoesNotClearError() = runTest {
        val fake = FakeLiveDataSource().apply {
            categoriesError = RuntimeException("Load failed")
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        // Verify there is a categoriesError
        assertNotNull(vm.uiState.value.categoriesError)

        // Emit non-authoritative snapshot
        fake.observeFlow.emit(LiveCategoryVisibilitySnapshot(totalCategoryCount = 0, visibleCategories = emptyList()))
        advanceUntilIdle()

        // Error must NOT be cleared by a non-authoritative snapshot
        assertNotNull(vm.uiState.value.categoriesError)

        // Emit authoritative snapshot
        fake.observeFlow.emit(LiveCategoryVisibilitySnapshot(totalCategoryCount = 1, visibleCategories = listOf(createCategory("cat1", "Category 1"))))
        advanceUntilIdle()

        // Now it should be cleared
        assertNull(vm.uiState.value.categoriesError)
    }

    // 43. Provider change: old provider's authoritative snapshot cannot make the new provider ready
    @Test
    fun testOldProviderAuthoritativeSnapshotCannotMakeNewProviderReady() = runTest {
        val fake = FakeLiveDataSource().apply {
            // Setup authoritative snapshot for old provider, but with 1000ms delay
            visibilitySnapshotsByProvider["prov_old"] = listOf(
                LiveCategoryVisibilitySnapshot(totalCategoryCount = 1, visibleCategories = listOf(createCategory("cat1", "Category 1")))
            )
            observeDelayByProvider["prov_old"] = 1000L
        }
        val vm = LiveViewModel(fake, fakeFavorites)
        vm.onProfileChanged(createEnabledProfile("prov_old"))
        advanceTimeBy(500L) // prov_old is still waiting for delay to complete

        // Switch to prov_new, which has no snapshot emissions and will be waiting on observeFlow
        vm.onProfileChanged(createEnabledProfile("prov_new"))
        advanceUntilIdle() // let the 1000ms delay of prov_old finish in the background

        // The UI should NOT be ready because prov_old's delayed emission should be ignored
        assertFalse(vm.uiState.value.categoryVisibilityReady)
    }

    // --- NEW TESTS ---

    @Test
    fun testLiveDisabledCleansUpFavorites() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        // Start as enabled with favorites
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        // Toggle favorite to create mutation state
        val channel = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L // delayed mutation
        vm.toggleFavorite(channel)
        advanceTimeBy(100L)
        
        // Confirm there is an active mutation and favorited ID
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertTrue(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        
        // Now disable Live
        vm.onProfileChanged(createDisabledProfile("prov_1"))
        advanceUntilIdle()
        
        val state = vm.uiState.value
        assertFalse(state.favoritesEnabled)
        assertTrue(state.favoriteChannelIds.isEmpty())
        assertTrue(state.favoriteMutationChannelIds.isEmpty())
        assertNull(state.favoritesError)
        assertFalse(state.favoritesLoading)
    }

    @Test
    fun testFavoritesDisabledCleansUpButDoesNotReload() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val catCallCountBefore = fake.categoriesCallCount
        val chanCallCountBefore = fake.channelsCallCount
        
        // Now disable favorites only
        val disabledFavProfile = profile.copy(
            features = profile.features.copy(
                favoritesEnabled = false
            )
        )
        vm.onProfileChanged(disabledFavProfile)
        advanceUntilIdle()
        
        // Verify no extra category or channel loads
        assertEquals(catCallCountBefore, fake.categoriesCallCount)
        assertEquals(chanCallCountBefore, fake.channelsCallCount)
        
        // Verify favorites state is cleared
        val state = vm.uiState.value
        assertFalse(state.favoritesEnabled)
        assertTrue(state.favoriteChannelIds.isEmpty())
        assertTrue(state.favoriteMutationChannelIds.isEmpty())
        assertNull(state.favoritesError)
    }

    @Test
    fun testTransitionFavoritesEnabledLaunchesObserver() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        // Favorites initially disabled
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = false
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        assertEquals(0, fakeFavorites.observeFavoritesCallCount)
        assertFalse(vm.uiState.value.favoritesEnabled)
        
        // Enable favorites
        val enabledProfile = profile.copy(
            features = profile.features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(enabledProfile)
        advanceUntilIdle()
        
        assertEquals(1, fakeFavorites.observeFavoritesCallCount)
        assertTrue(vm.uiState.value.favoritesEnabled)
    }

    @Test
    fun testProviderChangeClearsAndStartsNewFavoritesObserver() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile1 = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile1)
        advanceUntilIdle()
        
        // Emit favorites for prov_1
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertEquals(1, fakeFavorites.observeFavoritesCallCount)
        
        // Change provider
        fakeFavorites.observeFlow.resetReplayCache()
        val profile2 = createEnabledProfile("prov_2").copy(
            features = createEnabledProfile("prov_2").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile2)
        advanceUntilIdle()
        
        // Verify state is cleared
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
        assertEquals(2, fakeFavorites.observeFavoritesCallCount)
    }

    @Test
    fun testToggleFavoriteOptimisticUpdateAndCall() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        
        vm.toggleFavorite(channel)
        
        // Verify optimistic addition
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertTrue(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        
        advanceUntilIdle()
        assertEquals(1, fakeFavorites.addFavoriteCallCount)
    }

    @Test
    fun testToggleFavoriteIgnoresRedundantCallsWhilePending() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        
        vm.toggleFavorite(channel)
        vm.toggleFavorite(channel) // redundant call
        
        advanceUntilIdle()
        assertEquals(1, fakeFavorites.addFavoriteCallCount)
    }

    @Test
    fun testMutationSuccessRetainsStateAndClearsIndicator() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        
        // Wait for completion
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertFalse(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        assertNull(vm.uiState.value.favoritesError)
    }

    @Test
    fun testMutationFailureRevertsAndShowsError() = runTest {
        val fake = FakeLiveDataSource()
        fakeFavorites.addFavoriteError = RuntimeException("Failed")
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        
        advanceUntilIdle()
        
        // Reverted
        assertFalse(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertFalse(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        assertEquals("Could not update this favorite.", vm.uiState.value.favoritesError)
    }

    @Test
    fun testMutationCancellationRevertsStateAndShowsNoError() = runTest {
        val fake = FakeLiveDataSource()
        fakeFavorites.addFavoriteDelayMs = 1000L
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        
        // Cancel favorite work via disabling favorites before delay finishes
        val disabledProfile = profile.copy(
            features = profile.features.copy(favoritesEnabled = false)
        )
        vm.onProfileChanged(disabledProfile)
        advanceUntilIdle()
        
        // State should be reverted and cleared of errors/indicators
        assertFalse(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertFalse(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        assertNull(vm.uiState.value.favoritesError)
    }

    @Test
    fun testOldProviderMutationCompletionIgnored() = runTest {
        val fake = FakeLiveDataSource()
        fakeFavorites.addFavoriteDelayMs = 1000L
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile1 = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile1)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        
        // Change provider before mutation finishes
        val profile2 = createEnabledProfile("prov_2").copy(
            features = createEnabledProfile("prov_2").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile2)
        advanceUntilIdle()
        
        // Current state (prov_2) should not have chan1 as favorite or mutation
        assertFalse(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertFalse(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        assertNull(vm.uiState.value.favoritesError)
    }

    @Test
    fun testEstablishedObserverFailureRetainsLastKnownFavorites() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        
        // Trigger error in observer
        fakeFavorites.observeFavoritesError = RuntimeException("Error")
        fakeFavorites.observeFlow.emit(emptyList()) // trigger collect emission
        advanceUntilIdle()
        
        // Favorites should still contain chan1 (retains last known)
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertEquals("Could not load Live TV favorites.", vm.uiState.value.favoritesError)
        assertFalse(vm.uiState.value.favoritesLoading)
    }

    @Test
    fun testDismissFavoritesError() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        // Inject failure
        fakeFavorites.observeFavoritesError = RuntimeException("Error")
        fakeFavorites.observeFlow.emit(emptyList())
        advanceUntilIdle()
        
        assertEquals("Could not load Live TV favorites.", vm.uiState.value.favoritesError)
        
        vm.dismissFavoritesError()
        assertNull(vm.uiState.value.favoritesError)
    }

    @Test
    fun testActiveJobsCancelledOnCleared() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites)
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val onClearedMethod = vm::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)
        
        advanceUntilIdle()
        assertFalse(vm.uiState.value.favoritesLoading)
    }
}
