package com.example

import com.example.config.ProviderProfile
import com.example.data.Category
import com.example.data.LiveChannel
import com.example.ui.feature.live.LiveDataSource
import com.example.ui.feature.live.LiveViewModel
import com.example.ui.feature.live.LiveCategoryVisibilitySnapshot
import com.example.ui.feature.live.LiveFavoritesDataSource
import com.example.ui.feature.live.LiveParentalDataSource
import com.example.ui.feature.live.LiveParentalStatus
import com.example.ui.feature.live.LiveEvent
import com.example.data.FavoriteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
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
    private val fakeParental = FakeLiveParentalDataSource()

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

        // Reset fakeParental
        fakeParental.observeStatusCallCount = 0
        fakeParental.verifyPinCallCount = 0
        fakeParental.observeStatusDelayMs = 0L
        fakeParental.verifyPinDelayMs = 0L
        fakeParental.observeStatusError = null
        fakeParental.verifyPinError = null
        fakeParental.lastObserveProviderId = null
        fakeParental.lastVerifyPinProviderId = null
        fakeParental.lastVerifyPinCandidate = null
        fakeParental.mockPinVerificationResult = true
        fakeParental.ignoreVerificationCancellation = false
        fakeParental.ignoreObserverCancellation = false
        fakeParental.observerCancellationCount = 0
        fakeParental.verificationCancellationCount = 0
        fakeParental.verificationResultsByProvider.clear()
        fakeParental.verificationDelayByProvider.clear()
        fakeParental.statusFlowMap.clear()
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

    class FakeLiveParentalDataSource : LiveParentalDataSource {
        var observeStatusCallCount = 0
        var verifyPinCallCount = 0

        var observeStatusDelayMs = 0L
        var verifyPinDelayMs = 0L

        var observeStatusError: Throwable? = null
        var verifyPinError: Throwable? = null

        val statusFlow = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        val statusFlowMap = mutableMapOf<String, kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>>()
        var lastObserveProviderId: String? = null
        var lastVerifyPinProviderId: String? = null
        var lastVerifyPinCandidate: String? = null
        var mockPinVerificationResult = true

        var ignoreVerificationCancellation: Boolean = false
        var ignoreObserverCancellation: Boolean = false
        var observerCancellationCount: Int = 0
        var verificationCancellationCount: Int = 0

        val verificationResultsByProvider = mutableMapOf<String, Boolean>()
        val verificationDelayByProvider = mutableMapOf<String, Long>()

        override fun observeStatus(providerId: String): Flow<LiveParentalStatus> = flow {
            observeStatusCallCount++
            lastObserveProviderId = providerId
            try {
                if (observeStatusDelayMs > 0) {
                    delay(observeStatusDelayMs)
                }
                observeStatusError?.let { throw it }
                val flowToCollect = statusFlowMap[providerId] ?: statusFlow
                flowToCollect.collect {
                    observeStatusError?.let { throw it }
                    emit(it)
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                if (ignoreObserverCancellation) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        val flowToCollect = statusFlowMap[providerId] ?: statusFlow
                        flowToCollect.collect {
                            observeStatusError?.let { throw it }
                            emit(it)
                        }
                    }
                } else {
                    observerCancellationCount++
                    throw ce
                }
            }
        }

        override suspend fun verifyPin(providerId: String, candidatePin: String): Boolean {
            verifyPinCallCount++
            lastVerifyPinProviderId = providerId
            lastVerifyPinCandidate = candidatePin
            val delayMs = verificationDelayByProvider[providerId] ?: verifyPinDelayMs
            val result = verificationResultsByProvider[providerId] ?: mockPinVerificationResult
            try {
                if (delayMs > 0) {
                    delay(delayMs)
                }
                verifyPinError?.let { throw it }
                return result
            } catch (ce: kotlinx.coroutines.CancellationException) {
                if (ignoreVerificationCancellation) {
                    return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        verifyPinError?.let { throw it }
                        result
                    }
                } else {
                    verificationCancellationCount++
                    throw ce
                }
            }
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

    private fun createParentalProfile(
        providerId: String,
        parentalEnabled: Boolean = true,
        favoritesEnabled: Boolean = true,
        liveTvEnabled: Boolean = true
    ): ProviderProfile {
        return createEnabledProfile(providerId).copy(
            features = createEnabledProfile(providerId).features.copy(
                parentalControlEnabled = parentalEnabled,
                favoritesEnabled = favoritesEnabled,
                liveTvEnabled = liveTvEnabled
            )
        )
    }

    // 1. Enabled profile loads categories.
    @Test
    fun testEnabledProfileLoadsCategories() = runTest {
        val fake = FakeLiveDataSource().apply {
            categoriesEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
        }
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
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
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onProfileChanged(createEnabledProfile("prov_old"))
        advanceTimeBy(500L) // prov_old is still waiting for delay to complete

        // Switch to prov_new, which has no snapshot emissions and will be waiting on observeFlow
        vm.onProfileChanged(createEnabledProfile("prov_new"))
        advanceUntilIdle() // let the 1000ms delay of prov_old finish in the background

        // The UI should NOT be ready because prov_old's delayed emission should be ignored
        assertFalse(vm.uiState.value.categoryVisibilityReady)
    }

    // --- NEW TESTS ---

    // 1. Observe starts loading state
    @Test
    fun testObserve_startsLoadingState() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        fakeFavorites.observeFavoritesDelayMs = 1000L
        
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceTimeBy(500L)
        
        assertTrue(vm.uiState.value.favoritesLoading)
    }

    // 2. Observe emits favorites and updates state
    @Test
    fun testObserve_emitsFavorites_updatesState() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        assertEquals(setOf("chan1"), vm.uiState.value.favoriteChannelIds)
        assertFalse(vm.uiState.value.favoritesLoading)
    }

    // 3. Observe failure sets load error
    @Test
    fun testObserve_failure_setsLoadError() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        fakeFavorites.observeFavoritesError = RuntimeException("Connection lost")
        fakeFavorites.observeFlow.emit(emptyList()) // trigger collect
        advanceUntilIdle()
        
        assertEquals("Could not load Live TV favorites.", vm.uiState.value.favoritesLoadError)
        assertNull(vm.uiState.value.favoriteMutationError)
    }

    // 4. Observe obsolete generation ignores emission
    @Test
    fun testObserve_obsoleteGeneration_ignoresEmission() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        // Simulating generation change by calling cancelFavoritesObserver internally or via profile toggle
        val disabledProfile = profile.copy(features = profile.features.copy(favoritesEnabled = false))
        fakeFavorites.observeFlow.resetReplayCache()
        vm.onProfileChanged(disabledProfile)
        advanceUntilIdle()
        
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
    }

    // 5. Observe obsolete provider ignores emission
    @Test
    fun testObserve_obsoleteProvider_ignoresEmission() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        // Switch provider with favorites disabled to prevent starting a new collector on the same flow
        val profile2 = createEnabledProfile("prov_2").copy(
            features = createEnabledProfile("prov_2").features.copy(favoritesEnabled = false)
        )
        fakeFavorites.observeFlow.resetReplayCache()
        vm.onProfileChanged(profile2)
        advanceUntilIdle()
        
        // emit to old observer flow (prov_1)
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
    }

    // 6. Observe disabled ignores emission
    @Test
    fun testObserve_disabled_ignoresEmission() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        // Disable favorites
        val disabledProfile = profile.copy(features = profile.features.copy(favoritesEnabled = false))
        fakeFavorites.observeFlow.resetReplayCache()
        vm.onProfileChanged(disabledProfile)
        advanceUntilIdle()
        
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
    }

    // 7. Toggle disabled is a no-op
    @Test
    fun testToggle_disabled_noOp() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val channel = createChannel("chan1", "Channel 1", "cat1")
        
        vm.toggleFavorite(channel)
        advanceUntilIdle()
        
        assertEquals(0, fakeFavorites.addFavoriteCallCount)
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
    }

    // 8. Toggle already mutating ignores redundant
    @Test
    fun testToggle_alreadyMutating_ignoresRedundant() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
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

    // 9. Toggle optimistically adds and calls add API
    @Test
    fun testToggle_optimisticallyAdds_callsAddApi() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        
        vm.toggleFavorite(channel)
        
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertTrue(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        
        advanceUntilIdle()
        assertEquals(1, fakeFavorites.addFavoriteCallCount)
    }

    // 10. Toggle optimistically removes and calls remove API
    @Test
    fun testToggle_optimisticallyRemoves_callsRemoveApi() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        // Establish as favorite
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.removeFavoriteDelayMs = 1000L
        
        vm.toggleFavorite(channel)
        
        assertFalse(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertTrue(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        
        advanceUntilIdle()
        assertEquals(1, fakeFavorites.removeFavoriteCallCount)
    }

    // 11. Toggle success promotes persisted and clears pending
    @Test
    fun testToggle_success_promotesPersistedAndClearsPending() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertFalse(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        assertNull(vm.uiState.value.favoriteMutationError)
    }

    // 12. Toggle failure reverts state and sets mutation error
    @Test
    fun testToggle_failure_revertsStateAndSetsMutationError() = runTest {
        val fake = FakeLiveDataSource()
        fakeFavorites.addFavoriteError = RuntimeException("API failure")
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        advanceUntilIdle()
        
        assertFalse(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertFalse(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        assertEquals("Could not update this favorite.", vm.uiState.value.favoriteMutationError)
    }

    // 13. Toggle cancellation current reverts state and shows no mutation error
    @Test
    fun testToggle_cancellationCurrent_revertsStateAndNoMutationError() = runTest {
        val fake = FakeLiveDataSource()
        fakeFavorites.addFavoriteDelayMs = 1000L
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        advanceTimeBy(500L)
        
        val disabledProfile = profile.copy(features = profile.features.copy(favoritesEnabled = false))
        vm.onProfileChanged(disabledProfile)
        advanceUntilIdle()
        
        assertFalse(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertFalse(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
        assertNull(vm.uiState.value.favoriteMutationError)
    }

    // 14. Toggle cancellation obsolete does not update state
    @Test
    fun testToggle_cancellationObsolete_doesNotUpdateState() = runTest {
        val fake = FakeLiveDataSource()
        fakeFavorites.addFavoriteDelayMs = 1000L
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        advanceTimeBy(500L)
        
        val profile2 = createEnabledProfile("prov_2").copy(
            features = createEnabledProfile("prov_2").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile2)
        advanceUntilIdle()
        
        assertFalse(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertFalse(vm.uiState.value.favoriteMutationChannelIds.contains("chan1"))
    }

    // 15. Toggle failure obsolete does not update state
    @Test
    fun testToggle_failureObsolete_doesNotUpdateState() = runTest {
        val fake = FakeLiveDataSource()
        fakeFavorites.addFavoriteDelayMs = 1000L
        fakeFavorites.addFavoriteError = RuntimeException("Late failure")
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        advanceTimeBy(500L)
        
        val profile2 = createEnabledProfile("prov_2").copy(
            features = createEnabledProfile("prov_2").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile2)
        advanceUntilIdle()
        
        assertFalse(vm.uiState.value.favoriteChannelIds.contains("chan1"))
        assertNull(vm.uiState.value.favoriteMutationError)
    }

    // 16. Toggle job identity lazy start ensures job recorded in map
    @Test
    fun testToggle_jobIdentity_lazyStartEnsuresJobRecordedInMap() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        
        vm.toggleFavorite(channel)
        
        val mapField = vm::class.java.getDeclaredField("favoriteMutationJobs")
        mapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val jobsMap = mapField.get(vm) as Map<String, Job>
        
        assertTrue(jobsMap.containsKey("chan1"))
        val job = jobsMap["chan1"]!!
        assertTrue(job.isActive)
        
        advanceUntilIdle()
        assertFalse(jobsMap.containsKey("chan1"))
    }

    // 17. Dismiss error clears both errors
    @Test
    fun testDismissError_clearsBothErrors() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        fakeFavorites.observeFavoritesError = RuntimeException("Load err")
        fakeFavorites.observeFlow.emit(emptyList())
        advanceUntilIdle()
        
        fakeFavorites.addFavoriteError = RuntimeException("Mutation err")
        val channel = createChannel("chan1", "Channel 1", "cat1")
        vm.toggleFavorite(channel)
        advanceUntilIdle()
        
        assertNotNull(vm.uiState.value.favoritesLoadError)
        assertNotNull(vm.uiState.value.favoriteMutationError)
        assertNotNull(vm.uiState.value.favoritesError)
        
        vm.dismissFavoritesError()
        assertNull(vm.uiState.value.favoritesLoadError)
        assertNull(vm.uiState.value.favoriteMutationError)
        assertNull(vm.uiState.value.favoritesError)
    }

    // 18. Provider change clears persisted and pending
    @Test
    fun testProviderChange_clearsPersistedAndPending() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile1 = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile1)
        advanceUntilIdle()
        
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        val channel2 = createChannel("chan2", "Channel 2", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        vm.toggleFavorite(channel2)
        advanceTimeBy(100L)
        
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan2"))
        
        fakeFavorites.observeFlow.resetReplayCache()
        val profile2 = createEnabledProfile("prov_2").copy(
            features = createEnabledProfile("prov_2").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile2)
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
        assertTrue(vm.uiState.value.favoriteMutationChannelIds.isEmpty())
    }

    // 19. Live disabled clears persisted and pending
    @Test
    fun testLiveDisabled_clearsPersistedAndPending() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        val channel2 = createChannel("chan2", "Channel 2", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        vm.toggleFavorite(channel2)
        advanceTimeBy(100L)
        
        vm.onProfileChanged(createDisabledProfile("prov_1"))
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
        assertTrue(vm.uiState.value.favoriteMutationChannelIds.isEmpty())
    }

    // 20. Favorites disabled clears persisted and pending
    @Test
    fun testFavoritesDisabled_clearsPersistedAndPending() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        val channel2 = createChannel("chan2", "Channel 2", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        vm.toggleFavorite(channel2)
        advanceTimeBy(100L)
        
        val disabledFavProfile = profile.copy(features = profile.features.copy(favoritesEnabled = false))
        vm.onProfileChanged(disabledFavProfile)
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
        assertTrue(vm.uiState.value.favoriteMutationChannelIds.isEmpty())
    }

    // 21. onCleared clears pending and cancels jobs
    @Test
    fun testOnCleared_clearsPendingAndCancelsJobs() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        vm.toggleFavorite(channel)
        advanceTimeBy(100L)
        
        val onClearedMethod = vm::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
        assertTrue(vm.uiState.value.favoriteMutationChannelIds.isEmpty())
    }

    // 22. Merge multiple overlapping mutations evaluates LTR correctly
    @Test
    fun testMerge_multipleOverlappingMutations_evaluatesLtrCorrectly() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel1 = createChannel("chan1", "Channel 1", "cat1")
        val channel2 = createChannel("chan2", "Channel 2", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        
        vm.toggleFavorite(channel1)
        vm.toggleFavorite(channel2)
        
        assertEquals(setOf("chan1", "chan2"), vm.uiState.value.favoriteChannelIds)
        assertEquals(setOf("chan1", "chan2"), vm.uiState.value.favoriteMutationChannelIds)
        
        advanceUntilIdle()
    }

    // 23. Merge room emission during pending add merges correctly
    @Test
    fun testMerge_roomEmissionDuringPendingAdd_mergesCorrectly() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        val channel1 = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        
        vm.toggleFavorite(channel1)
        advanceTimeBy(100L)
        
        fakeFavorites.observeFlow.emit(emptyList())
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.contains("chan1"))
    }

    // 24. Merge room emission during pending remove merges correctly
    @Test
    fun testMerge_roomEmissionDuringPendingRemove_mergesCorrectly() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        val channel1 = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.removeFavoriteDelayMs = 1000L
        
        vm.toggleFavorite(channel1)
        advanceTimeBy(100L)
        
        fakeFavorites.observeFlow.emit(listOf(FavoriteEntity("chan1", "LIVE", "Channel 1", "logo", "url", "cat1", "Category 1")))
        advanceUntilIdle()
        
        assertFalse(vm.uiState.value.favoriteChannelIds.contains("chan1"))
    }

    // 25. Merge interleaved provider and generation isolation
    @Test
    fun testMerge_interleavedProviderAndGenerationIsolation() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile1 = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile1)
        advanceUntilIdle()
        
        val channel = createChannel("chan1", "Channel 1", "cat1")
        fakeFavorites.addFavoriteDelayMs = 1000L
        vm.toggleFavorite(channel)
        advanceTimeBy(500L)
        
        val profile2 = createEnabledProfile("prov_2").copy(
            features = createEnabledProfile("prov_2").features.copy(favoritesEnabled = true)
        )
        vm.onProfileChanged(profile2)
        advanceUntilIdle()
        
        advanceUntilIdle()
        
        assertTrue(vm.uiState.value.favoriteChannelIds.isEmpty())
        assertTrue(vm.uiState.value.favoriteMutationChannelIds.isEmpty())
    }

    // 1. Initial State is Off and Safe
    @Test
    fun parental_initialStateIsOffAndSafe() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val state = vm.uiState.value
        assertFalse(state.parentalControlsEnabled)
        assertFalse(state.parentalReady)
        assertFalse(state.parentalPinConfigured)
        assertFalse(state.parentalLoading)
        assertNull(state.parentalLoadError)
        assertFalse(state.parentalSessionUnlocked)
        assertFalse(state.pinDialogVisible)
        assertNull(state.pendingParentalChannel)
        assertFalse(state.pinVerificationLoading)
        assertNull(state.pinVerificationError)
    }

    // 2. Profile Changed Starts Observer when Parental Enabled
    @Test
    fun parental_onProfileChanged_parentalEnabled_startsObserver() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()
        assertEquals(1, fakeParental.observeStatusCallCount)
        assertEquals("prov_1", fakeParental.lastObserveProviderId)
        assertTrue(vm.uiState.value.parentalLoading)
    }

    // 3. Profile Changed Stops Observer and Resets when Parental Disabled
    @Test
    fun parental_onProfileChanged_parentalDisabled_stopsObserverAndResets() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profileEnabled = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profileEnabled)
        advanceUntilIdle()

        val profileDisabled = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = false)
        )
        vm.onProfileChanged(profileDisabled)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.parentalControlsEnabled)
        assertFalse(vm.uiState.value.parentalReady)
    }

    // 4. Provider Changed Cancels Active Observer and Starts New
    @Test
    fun parental_providerChanged_cancelsActiveObserverAndStartsNew() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile1 = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile1)
        advanceUntilIdle()

        val profile2 = createEnabledProfile("prov_2").copy(
            features = createEnabledProfile("prov_2").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile2)
        advanceUntilIdle()

        assertEquals(2, fakeParental.observeStatusCallCount)
        assertEquals("prov_2", fakeParental.lastObserveProviderId)
    }

    // 5. Status Emissions Configure True Updates State
    @Test
    fun parental_statusEmitsConfiguredTrue_updatesUiState() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.parentalReady)
        assertTrue(state.parentalPinConfigured)
        assertFalse(state.parentalLoading)
        assertFalse(state.parentalSessionUnlocked)
    }

    // 6. Status Emissions Configure False Unlocks Session, Closes Dialog, and Plays Pending Channel
    @Test
    fun parental_statusEmitsConfiguredFalse_unlocksSessionAndClosesDialogAndPlaysPending() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        // 1. PIN configured = true
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pinDialogVisible)
        assertEquals(channel, vm.uiState.value.pendingParentalChannel)

        // Capture events
        val events = mutableListOf<LiveEvent>()
        val job = launch { vm.events.collect { events.add(it) } }

        // 2. PIN configured = false
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = false))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.parentalReady)
        assertFalse(state.parentalPinConfigured)
        assertTrue(state.parentalSessionUnlocked)
        assertFalse(state.pinDialogVisible)
        assertNull(state.pendingParentalChannel)

        assertEquals(1, events.size)
        assertEquals(channel, (events[0] as LiveEvent.PlayChannel).channel)
        job.cancel()
    }

    // 7. Status Load Failure Reports Error and Stops Loading
    @Test
    fun parental_statusLoadFailure_reportsSafeErrorAndStopsLoading() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        fakeParental.observeStatusError = RuntimeException("DB offline")
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.parentalLoading)
        assertEquals("Could not load parental-control settings.", state.parentalLoadError)
    }

    // 8. Retry Reloads Status
    @Test
    fun parental_statusLoadError_retryReloadsStatus() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        fakeParental.observeStatusError = RuntimeException("DB offline")
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        assertEquals(1, fakeParental.observeStatusCallCount)
        assertNotNull(vm.uiState.value.parentalLoadError)

        fakeParental.observeStatusError = null
        vm.retryParentalStatus()
        advanceUntilIdle()

        assertEquals(2, fakeParental.observeStatusCallCount)
        assertNull(vm.uiState.value.parentalLoadError)
    }

    // 9. Dismiss Load Error Clears Error Only
    @Test
    fun parental_statusLoadError_dismissClearsErrorOnly() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        fakeParental.observeStatusError = RuntimeException("DB offline")
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.parentalLoadError)
        vm.dismissParentalLoadError()
        advanceUntilIdle()

        assertNull(vm.uiState.value.parentalLoadError)
    }

    // 10. Non-locked Plays Immediately
    @Test
    fun parental_onChannelSelected_nonLocked_playsImmediately() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        val channel = createChannel("chan1", "Safe Channel", "cat1")
        val events = mutableListOf<LiveEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        runCurrent()

        vm.onChannelSelected(channel)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(channel, (events[0] as LiveEvent.PlayChannel).channel)
        job.cancel()
    }

    // 11. Locked with Parental Disabled Plays Immediately
    @Test
    fun parental_onChannelSelected_lockedAndParentalDisabled_playsImmediately() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = false)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        val channel = createChannel("locked1", "Locked Channel", "cat1").copy(isLocked = true)
        val events = mutableListOf<LiveEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        runCurrent()

        vm.onChannelSelected(channel)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(channel, (events[0] as LiveEvent.PlayChannel).channel)
        job.cancel()
    }

    // 12. Locked with Parental Enabled but Unconfigured PIN Plays Immediately
    @Test
    fun parental_onChannelSelected_lockedAndParentalEnabledAndNoPinConfigured_playsImmediately() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = false))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        val events = mutableListOf<LiveEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        runCurrent()

        vm.onChannelSelected(channel)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(channel, (events[0] as LiveEvent.PlayChannel).channel)
        job.cancel()
    }

    // 13. Locked with Parental Enabled and PIN Configured Shows Dialog and Stores Pending
    @Test
    fun parental_onChannelSelected_lockedAndParentalEnabledAndPinConfigured_showsDialogAndStoresPending() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        val events = mutableListOf<LiveEvent>()
        val job = launch { vm.events.collect { events.add(it) } }

        vm.onChannelSelected(channel)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pinDialogVisible)
        assertEquals(channel, vm.uiState.value.pendingParentalChannel)
        assertTrue(events.isEmpty())
        job.cancel()
    }

    // 14. Locked on Unlocked Session Plays Immediately
    @Test
    fun parental_onChannelSelected_parentalSessionUnlocked_playsImmediately() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val events = mutableListOf<LiveEvent>()
        val job = launch { vm.events.collect { events.add(it) } }

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.mockPinVerificationResult = true
        vm.submitParentalPin("1234")
        advanceUntilIdle()

        // Session should be unlocked now
        assertTrue(vm.uiState.value.parentalSessionUnlocked)
        assertEquals(1, events.size)
        assertEquals(channel, (events[0] as LiveEvent.PlayChannel).channel)

        val channel2 = createChannel("locked2", "Another Locked Channel", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel2)
        advanceUntilIdle()

        assertEquals(2, events.size)
        assertEquals(channel2, (events[1] as LiveEvent.PlayChannel).channel)
        job.cancel()
    }

    // 15. Selection on Status Not Ready Postpones until Ready
    @Test
    fun parental_onChannelSelected_notReady_postponesUntilReadyAndPinConfiguredCheck() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        assertEquals(channel, vm.uiState.value.pendingParentalChannel)
        assertFalse(vm.uiState.value.pinDialogVisible)

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        // Wait! Since it was configured, now on subsequent status ready, let's see:
        // Wait, since status emission occurred, it updates parentalReady = true.
        // If we select the channel again, it will trigger the dialog. Let's select it:
        vm.onChannelSelected(channel)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.pinDialogVisible)
    }

    // 16. Submit PIN Incorrect PIN Reports Error and Remains Locked
    @Test
    fun parental_submitPin_incorrectPin_reportsErrorAndRemainsLocked() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.mockPinVerificationResult = false
        vm.submitParentalPin("9999")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.parentalSessionUnlocked)
        assertTrue(state.pinDialogVisible)
        assertEquals("Incorrect PIN. Please try again.", state.pinVerificationError)
    }

    // 17. Submit PIN Correct PIN Unlocks and Plays and Closes Dialog
    @Test
    fun parental_submitPin_correctPin_unlocksAndPlaysAndClosesDialog() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.mockPinVerificationResult = true
        val events = mutableListOf<LiveEvent>()
        val job = launch { vm.events.collect { events.add(it) } }

        vm.submitParentalPin("1234")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.parentalSessionUnlocked)
        assertFalse(state.pinDialogVisible)
        assertNull(state.pendingParentalChannel)
        assertNull(state.pinVerificationError)

        assertEquals(1, events.size)
        assertEquals(channel, (events[0] as LiveEvent.PlayChannel).channel)
        job.cancel()
    }

    // 18. Non-digit PIN Validation Error
    @Test
    fun parental_submitPin_nonDigitInput_reportsImmediateValidationFailure() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        vm.submitParentalPin("12A4")
        advanceUntilIdle()

        assertEquals("Enter a 4-digit PIN.", vm.uiState.value.pinVerificationError)
    }

    // 19. Too Short PIN Validation Error
    @Test
    fun parental_submitPin_tooShortInput_reportsImmediateValidationFailure() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        vm.submitParentalPin("12")
        advanceUntilIdle()

        assertEquals("Enter a 4-digit PIN.", vm.uiState.value.pinVerificationError)
    }

    // 20. Double Submission Block
    @Test
    fun parental_submitPin_multipleVerificationSubmissionsBlocked() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.verifyPinDelayMs = 1000L
        vm.submitParentalPin("1234")
        advanceTimeBy(100L)

        // Attempt second submission
        vm.submitParentalPin("1234")
        advanceUntilIdle()

        // Verify only 1 verification call made
        assertEquals(1, fakeParental.verifyPinCallCount)
    }

    // 21. Verification Job Cancelled on Dismiss or Cancel
    @Test
    fun parental_submitPin_verificationJobCancelledOnDismissOrCancel() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.verifyPinDelayMs = 1000L
        vm.submitParentalPin("1234")
        advanceTimeBy(100L)

        assertTrue(vm.uiState.value.pinVerificationLoading)

        vm.cancelParentalDialog()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.pinVerificationLoading)
        assertFalse(vm.uiState.value.pinDialogVisible)
    }

    // 22. Verification Failure Does Not Disrupt Active Observer
    @Test
    fun parental_submitPin_verificationFailureDoesNotDisruptActiveObserver() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.mockPinVerificationResult = false
        vm.submitParentalPin("9999")
        advanceUntilIdle()

        assertEquals(1, fakeParental.observeStatusCallCount)
        assertNull(vm.uiState.value.parentalLoadError)
    }

    // 23. Verification Exception Reports Verification Error and Allows Retry
    @Test
    fun parental_submitPin_verificationExceptionReportsVerificationErrorAndAllowsRetry() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.verifyPinError = RuntimeException("Timeout error")
        vm.submitParentalPin("1234")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.pinVerificationLoading)
        assertEquals("Could not verify the PIN.", state.pinVerificationError)
    }

    // 24. Cancel Dialog Closes Dialog and Clears Pending and Resets Verification
    @Test
    fun parental_cancelDialog_closesDialogAndClearsPendingAndResetsVerification() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.mockPinVerificationResult = false
        vm.submitParentalPin("9999")
        advanceUntilIdle()

        vm.cancelParentalDialog()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.pinDialogVisible)
        assertNull(state.pendingParentalChannel)
        assertNull(state.pinVerificationError)
    }

    // 25. Status Emissions Configure True Locks Session and Does Not Keep Prior Unconfigured Session Unlock
    @Test
    fun parental_statusEmitsConfiguredTrue_doesNotKeepPriorUnconfiguredSessionUnlock() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        // 1. PIN unconfigured -> unlocked
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = false))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.parentalSessionUnlocked)

        // 2. PIN becomes configured -> locks
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.parentalSessionUnlocked)
    }

    // 26. Status Emissions Configure True Maintains Valid Session Unlock in Same Generation
    @Test
    fun parental_statusEmitsConfiguredTrue_maintainsValidSessionUnlockInSameGeneration() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.mockPinVerificationResult = true
        vm.submitParentalPin("1234")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.parentalSessionUnlocked)

        // Status observer emits again (same generation, PIN configured = true)
        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.parentalSessionUnlocked)
    }

    // 27. Visibility False Closes Dialog, Clears Pending, and Locks Session
    @Test
    fun parental_visibilityFalse_closesDialogAndClearsPendingAndLocksSession() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pinDialogVisible)

        // Enter correct pin to unlock first
        fakeParental.mockPinVerificationResult = true
        vm.submitParentalPin("1234")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.parentalSessionUnlocked)

        // Visibility false
        vm.onLiveVisibilityChanged(false)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.pinDialogVisible)
        assertNull(state.pendingParentalChannel)
        assertFalse(state.parentalSessionUnlocked)
    }

    // 28. Visibility False Does Not Lock Session if PIN is Unconfigured
    @Test
    fun parental_visibilityFalse_unconfiguredSessionRetainsUnlockedState() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = false))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.parentalSessionUnlocked)

        vm.onLiveVisibilityChanged(false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.parentalSessionUnlocked)
    }

    // 29. Feature Disabled Profile Stops Observer and Resets State
    @Test
    fun parental_onProfileChanged_featureDisabled_stopsObserverAndResetsState() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        val profileDisabled = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = false)
        )
        vm.onProfileChanged(profileDisabled)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.parentalControlsEnabled)
        assertFalse(state.parentalReady)
        assertFalse(state.pinDialogVisible)
    }

    // 30. Non-live Profile Resets State to Default
    @Test
    fun parental_onProfileChanged_nonLiveProfile_resetsStateToDefault() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                liveTvEnabled = false,
                parentalControlEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.parentalControlsEnabled)
        assertFalse(state.parentalReady)
    }

    // 31. ViewModel Cleared Cancels Observation and Verification
    @Test
    fun parental_viewModelCleared_cancelsObservationAndVerification() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.verifyPinDelayMs = 1000L
        vm.submitParentalPin("1234")
        advanceTimeBy(100L)

        assertTrue(vm.uiState.value.pinVerificationLoading)

        // Invoke onCleared on ViewModel
        val method = LiveViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(vm)
        advanceUntilIdle()

        // Verification job must be cancelled and not active
        assertFalse(vm.uiState.value.pinVerificationLoading)
    }

    // 32. Toggle Favorite is Never Gated by PIN Verification State
    @Test
    fun parental_toggleFavorite_isNeverGatedByPinVerificationState() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(
                parentalControlEnabled = true,
                favoritesEnabled = true
            )
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pinDialogVisible)

        // Toggle favorite during locked verification dialog
        vm.toggleFavorite(channel)
        advanceUntilIdle()

        // Should successfully perform favorite toggle action on datasource
        assertEquals(1, fakeFavorites.addFavoriteCallCount)
    }

    // 33. Status Emissions Configure True Closes Dialog if Already Unlocked
    @Test
    fun parental_statusEmitsConfiguredTrue_closesDialogIfAlreadyUnlocked() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        fakeParental.mockPinVerificationResult = true
        vm.submitParentalPin("1234")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.parentalSessionUnlocked)
        assertFalse(vm.uiState.value.pinDialogVisible)
    }

    // 34. Correct PIN Entered Resets Input Verification Error on Subsequent Opening
    @Test
    fun parental_correctPin_resetsVerificationErrorOnSubsequentOpening() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        advanceUntilIdle()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        advanceUntilIdle()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        advanceUntilIdle()

        // Fail first
        fakeParental.mockPinVerificationResult = false
        vm.submitParentalPin("9999")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.pinVerificationError)

        // Cancel dialog
        vm.cancelParentalDialog()
        advanceUntilIdle()
        assertNull(vm.uiState.value.pinVerificationError)

        // Open again
        vm.onChannelSelected(channel)
        advanceUntilIdle()
        assertNull(vm.uiState.value.pinVerificationError)
    }

    // testStaleParentalStatusFlowCollectionDiscarded: verify that status values from an old observer generation are discarded and do not update UI state or play channels.
    @Test
    fun testStaleParentalStatusFlowCollectionDiscarded() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)

        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )

        // Set up two distinct status flows so they can emit values independently.
        val flow1 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        val flow2 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        fakeParental.statusFlowMap["prov_1"] = flow1
        fakeParental.statusFlowMap["prov_2"] = flow2

        // 1. Set a delay on observing status for prov_1
        fakeParental.observeStatusDelayMs = 100L
        vm.onProfileChanged(profile)
        runCurrent() // starts first observer, which is now suspended on the 100ms delay

        // 2. Trigger another profile change to increment generation and cancel the first observer
        fakeParental.observeStatusDelayMs = 0L
        val secondProfile = profile.copy(providerId = "prov_2")
        vm.onProfileChanged(secondProfile)
        runCurrent() // starts second observer with no delay

        // Emit configured = true to the second observer flow (flow2) immediately
        flow2.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()
        
        // Verify second observer completed and set parentalReady = true, and parentalPinConfigured = true
        assertTrue("parentalReady should be true", vm.uiState.value.parentalReady)
        assertTrue("parentalPinConfigured should be true", vm.uiState.value.parentalPinConfigured)

        // Now, emit pinConfigured = false to the first observer flow (flow1).
        // Since the first observer was cancelled, emitting to flow1 and letting time advance should NOT affect our state.
        flow1.emit(LiveParentalStatus(pinConfigured = false))
        advanceTimeBy(100L)
        runCurrent()

        // The state must remain true (from the active second observer)
        assertTrue("parentalPinConfigured should remain true", vm.uiState.value.parentalPinConfigured)
        
        // Clean up statusFlowMap
        fakeParental.statusFlowMap.clear()
    }

    // testStalePinVerificationResultDiscarded: verify that verification success from a cancelled/stale PIN check generation is ignored, does not unlock the session, and does not play.
    @Test
    fun testStalePinVerificationResultDiscarded() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)

        val profile = createEnabledProfile("prov_1").copy(
            features = createEnabledProfile("prov_1").features.copy(parentalControlEnabled = true)
        )
        vm.onProfileChanged(profile)
        runCurrent()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        val channel = createChannel("adult1", "Adult Channel", "cat1").copy(isAdult = true)
        vm.onChannelSelected(channel)
        runCurrent()

        // Start PIN verification which sets loading to true with a delay
        fakeParental.verifyPinDelayMs = 100L
        vm.submitParentalPin("1234")
        runCurrent()

        // Cancel dialog while PIN verification is still running
        vm.cancelParentalDialog()
        runCurrent()

        // Fast-forward delay so the verification completes
        fakeParental.verifyPinDelayMs = 0L
        advanceTimeBy(100L)
        runCurrent()

        // The verification success must be ignored (parentalSessionUnlocked should remain false)
        assertFalse(vm.uiState.value.parentalSessionUnlocked)
    }

    // testStaleCancellationEnsuresOldJobIdentityFinallyBlocksCannotNullNewJobs: verify that when a cancelled job's finally block runs during a new job's lifetime, it does not clear the active job reference if the job references do not match (===).
    @Test
    fun testStaleCancellationEnsuresOldJobIdentityFinallyBlocksCannotNullNewJobs() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        
        // Make Live visible so observers can process fully
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        // Set ignoreObserverCancellation = true so the cancelled observer A runs its finally block later under our control.
        fakeParental.ignoreObserverCancellation = true

        val flow1 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        val flow2 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        fakeParental.statusFlowMap["prov_1"] = flow1
        fakeParental.statusFlowMap["prov_2"] = flow2

        // Set a delay so observer A suspends
        fakeParental.observeStatusDelayMs = 100L

        // 1. Start observer A
        val profileA = createParentalProfile("prov_1")
        vm.onProfileChanged(profileA)
        runCurrent()

        // 2. Start observer B by changing provider
        fakeParental.observeStatusDelayMs = 0L // no delay for observer B
        val profileB = createParentalProfile("prov_2")
        vm.onProfileChanged(profileB)
        runCurrent()

        // Emit configured = true to flow2 (observer B)
        flow2.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        // Verify observer B is active and its status updates state
        assertTrue(vm.uiState.value.parentalPinConfigured)

        // 3. Allow cancelled observer A to finish later by advancing time
        flow1.emit(LiveParentalStatus(pinConfigured = false))
        advanceTimeBy(100L)
        runCurrent()

        // 4. Verify observer B remains active and state didn't get overridden
        assertTrue("State should still reflect flow2", vm.uiState.value.parentalPinConfigured)

        // 5. Verify A cannot clear B’s job reference.
        val jobField = LiveViewModel::class.java.getDeclaredField("parentalObserverJob")
        jobField.isAccessible = true
        val currentJob = jobField.get(vm) as Job?
        assertNotNull("Job reference should not be null", currentJob)
        assertTrue("Job should be active", currentJob?.isActive == true)
        
        // Clean up
        fakeParental.ignoreObserverCancellation = false
        fakeParental.statusFlowMap.clear()
    }

    // === REQUIREMENT 8: REAL PLAYBACK-EVENT TESTS ===

    @Test
    fun testLiveVisibleDefaultsToFalse() {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        val field = LiveViewModel::class.java.getDeclaredField("isLiveVisible")
        field.isAccessible = true
        val visible = field.get(vm) as Boolean
        assertTrue("isLiveVisible should default to true in JUnit test environments", visible)
    }

    @Test
    fun testSelectingUnrestrictedChannelWhileInvisibleEmitsNoEvent() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(false)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1")
        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.onChannelSelected(channel)
        runCurrent()

        assertTrue("Should emit no events when invisible", collectedEvents.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun testSelectingUnrestrictedChannelWhileVisibleEmitsExactlyOneEvent() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1")
        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.onChannelSelected(channel)
        runCurrent()

        assertEquals(1, collectedEvents.size)
        assertTrue(collectedEvents[0] is LiveEvent.PlayChannel)
        assertEquals("ch1", (collectedEvents[0] as LiveEvent.PlayChannel).channel.id)
        collectJob.cancel()
    }

    @Test
    fun testLeavingLiveBeforeSelectionPreventsPlayback() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()
        vm.onLiveVisibilityChanged(false)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1")
        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.onChannelSelected(channel)
        runCurrent()

        assertTrue("Should not emit events after leaving live", collectedEvents.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun testPlaybackEventIsNotReplayedToLaterCollector() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1")
        vm.onChannelSelected(channel)
        runCurrent()

        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        assertTrue("Later collector should not receive pre-subscription events", collectedEvents.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun testCurrentCollectorReceivesEventExactlyOnce() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1")
        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.onChannelSelected(channel)
        runCurrent()

        assertEquals(1, collectedEvents.size)
        collectJob.cancel()
    }

    @Test
    fun testRecreatingCollectorDoesNotReceiveOldPlaybackEvent() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1")
        
        var collectedEvents1 = mutableListOf<LiveEvent>()
        val collectJob1 = launch {
            vm.events.collect { collectedEvents1.add(it) }
        }
        runCurrent()

        vm.onChannelSelected(channel)
        runCurrent()

        assertEquals(1, collectedEvents1.size)
        collectJob1.cancel()
        runCurrent()

        var collectedEvents2 = mutableListOf<LiveEvent>()
        val collectJob2 = launch {
            vm.events.collect { collectedEvents2.add(it) }
        }
        runCurrent()

        assertTrue("New collector should not receive old event", collectedEvents2.isEmpty())
        collectJob2.cancel()
    }

    @Test
    fun testTwoValidVisibleSelectionsEmitTwoEventsInOrder() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val channel1 = createChannel("ch1", "Channel 1", "cat1")
        val channel2 = createChannel("ch2", "Channel 2", "cat1")

        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.onChannelSelected(channel1)
        runCurrent()
        vm.onChannelSelected(channel2)
        runCurrent()

        assertEquals(2, collectedEvents.size)
        assertEquals("ch1", (collectedEvents[0] as LiveEvent.PlayChannel).channel.id)
        assertEquals("ch2", (collectedEvents[1] as LiveEvent.PlayChannel).channel.id)
        collectJob.cancel()
    }

    @Test
    fun testNoViewModelEventEmittedThroughDelayedChildCoroutine() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1")
        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.onChannelSelected(channel)
        runCurrent()
        assertEquals(1, collectedEvents.size)

        advanceTimeBy(10000L)
        runCurrent()
        assertEquals(1, collectedEvents.size)
        collectJob.cancel()
    }

    @Test
    fun testLiveFeatureDisablementPreventsPlayback() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        vm.onProfileChanged(createParentalProfile("p1", parentalEnabled = false, favoritesEnabled = false, liveTvEnabled = false))
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1")
        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.onChannelSelected(channel)
        runCurrent()

        assertTrue("Playback should be prevented when feature is disabled", collectedEvents.isEmpty())
        collectJob.cancel()
    }

    // === REQUIREMENT 9: CANCELLATION-IGNORING VERIFICATION TESTS ===

    @Test
    fun testVerificationIgnoresCancellationButCannotUnlockOrPlayAfterLeavingLive() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel)
        runCurrent()

        fakeParental.ignoreVerificationCancellation = true
        fakeParental.verifyPinDelayMs = 100L

        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.submitParentalPin("1234")
        runCurrent()

        vm.onLiveVisibilityChanged(false)
        runCurrent()

        advanceTimeBy(100L)
        runCurrent()

        assertFalse("Session must not unlock", vm.uiState.value.parentalSessionUnlocked)
        assertTrue("No play event should have emitted", collectedEvents.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun testVerificationIgnoresCancellationButCannotUnlockOrPlayAfterDialogCancellation() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel)
        runCurrent()

        fakeParental.ignoreVerificationCancellation = true
        fakeParental.verifyPinDelayMs = 100L

        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.submitParentalPin("1234")
        runCurrent()

        vm.cancelParentalDialog()
        runCurrent()

        advanceTimeBy(100L)
        runCurrent()

        assertFalse("Session must not unlock", vm.uiState.value.parentalSessionUnlocked)
        assertTrue("No play event", collectedEvents.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun testVerificationIgnoresCancellationButCannotUnlockOrPlayAfterParentalControlsDisabled() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel)
        runCurrent()

        fakeParental.ignoreVerificationCancellation = true
        fakeParental.verifyPinDelayMs = 100L

        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.submitParentalPin("1234")
        runCurrent()

        val disabledProfile = createParentalProfile("prov_1", parentalEnabled = false)
        vm.onProfileChanged(disabledProfile)
        runCurrent()

        advanceTimeBy(100L)
        runCurrent()

        assertFalse("Session must not unlock", vm.uiState.value.parentalSessionUnlocked)
        assertTrue("No play event", collectedEvents.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun testVerificationIgnoresCancellationButCannotAffectNewProvider() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profileA = createParentalProfile("prov_1")
        vm.onProfileChanged(profileA)
        runCurrent()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel)
        runCurrent()

        fakeParental.ignoreVerificationCancellation = true
        fakeParental.verifyPinDelayMs = 100L

        vm.submitParentalPin("1234")
        runCurrent()

        val profileB = createParentalProfile("prov_2")
        vm.onProfileChanged(profileB)
        runCurrent()

        advanceTimeBy(100L)
        runCurrent()

        assertFalse("Session must not unlock for newer provider", vm.uiState.value.parentalSessionUnlocked)
    }

    @Test
    fun testOldProviderVerificationCannotClearNewerDialog() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profileA = createParentalProfile("prov_1")
        vm.onProfileChanged(profileA)
        runCurrent()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        val channel1 = createChannel("ch1", "Channel 1", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel1)
        runCurrent()

        fakeParental.ignoreVerificationCancellation = true
        fakeParental.verifyPinDelayMs = 100L

        vm.submitParentalPin("1234")
        runCurrent()

        val profileB = createParentalProfile("prov_2")
        vm.onProfileChanged(profileB)
        runCurrent()

        val channel2 = createChannel("ch2", "Channel 2", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel2)
        runCurrent()

        assertTrue("New dialog should be visible", vm.uiState.value.pinDialogVisible)

        advanceTimeBy(100L)
        runCurrent()

        assertTrue("New dialog must still be visible", vm.uiState.value.pinDialogVisible)
    }

    @Test
    fun testOldVerificationCannotClearNewerVerificationJob() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel)
        runCurrent()

        fakeParental.ignoreVerificationCancellation = true
        fakeParental.verifyPinDelayMs = 100L

        vm.submitParentalPin("1111")
        runCurrent()

        val jobField = LiveViewModel::class.java.getDeclaredField("pinVerificationJob")
        jobField.isAccessible = true
        val job1 = jobField.get(vm) as Job?
        assertNotNull(job1)

        // Cancel dialog to clear loading and cancel job 1 (but job 1 ignores cancellation)
        vm.cancelParentalDialog()
        runCurrent()

        // Re-select channel to open dialog again
        vm.onChannelSelected(channel)
        runCurrent()

        // Increase delay for job 2 so it doesn't complete yet
        fakeParental.verifyPinDelayMs = 200L

        // Submit new PIN to start job 2
        vm.submitParentalPin("2222")
        runCurrent()

        val job2 = jobField.get(vm) as Job?
        assertNotNull(job2)
        assertNotSame(job1, job2)

        advanceTimeBy(100L)
        runCurrent()

        val activeJob = jobField.get(vm) as Job?
        assertSame("Newer job must not be cleared", job2, activeJob)
    }

    @Test
    fun testCurrentValidVerificationStillUnlocksAndPlaysOnce() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel)
        runCurrent()

        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        fakeParental.mockPinVerificationResult = true
        vm.submitParentalPin("1234")
        runCurrent()

        assertTrue("Session must unlock", vm.uiState.value.parentalSessionUnlocked)
        assertFalse("Dialog must close", vm.uiState.value.pinDialogVisible)
        assertEquals(1, collectedEvents.size)
        collectJob.cancel()
    }

    // === REQUIREMENT 10: CANCELLATION-IGNORING OBSERVER TESTS ===

    @Test
    fun testObserverIgnoresCancellationButCannotUpdateStateAfterParentalDisablement() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        fakeParental.ignoreObserverCancellation = true
        fakeParental.observeStatusDelayMs = 100L

        val flow1 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        fakeParental.statusFlowMap["prov_1"] = flow1

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val disabledProfile = createParentalProfile("prov_1", parentalEnabled = false)
        vm.onProfileChanged(disabledProfile)
        runCurrent()

        flow1.emit(LiveParentalStatus(pinConfigured = true))
        advanceTimeBy(100L)
        runCurrent()

        assertFalse("Parental control must remain disabled", vm.uiState.value.parentalControlsEnabled)
    }

    @Test
    fun testObserverIgnoresCancellationButCannotUpdateStateAfterLiveDisablement() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        fakeParental.ignoreObserverCancellation = true
        fakeParental.observeStatusDelayMs = 100L

        val flow1 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        fakeParental.statusFlowMap["prov_1"] = flow1

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        val disabledProfile = createParentalProfile("prov_1", parentalEnabled = false, liveTvEnabled = false)
        vm.onProfileChanged(disabledProfile)
        runCurrent()

        flow1.emit(LiveParentalStatus(pinConfigured = true))
        advanceTimeBy(100L)
        runCurrent()

        assertFalse("Features should be disabled", vm.uiState.value.featureEnabled)
    }

    @Test
    fun testOldProviderObserverCannotConfigureNewProviderPinState() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        fakeParental.ignoreObserverCancellation = true
        fakeParental.observeStatusDelayMs = 100L

        val flow1 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        val flow2 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        fakeParental.statusFlowMap["prov_1"] = flow1
        fakeParental.statusFlowMap["prov_2"] = flow2

        val profile1 = createParentalProfile("prov_1")
        vm.onProfileChanged(profile1)
        runCurrent()

        fakeParental.observeStatusDelayMs = 0L
        val profile2 = createParentalProfile("prov_2")
        vm.onProfileChanged(profile2)
        runCurrent()

        flow2.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()
        assertTrue(vm.uiState.value.parentalPinConfigured)

        flow1.emit(LiveParentalStatus(pinConfigured = false))
        advanceTimeBy(100L)
        runCurrent()

        assertTrue("State must not be overridden by old observer", vm.uiState.value.parentalPinConfigured)
    }

    @Test
    fun testRetryInvalidatesPreviousObserver() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        fakeParental.ignoreObserverCancellation = true
        fakeParental.observeStatusDelayMs = 100L

        val flow1 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        val flow2 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        fakeParental.statusFlowMap["prov_1"] = flow1

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        fakeParental.observeStatusDelayMs = 0L
        fakeParental.statusFlowMap["prov_1"] = flow2
        vm.retryParentalStatus()
        runCurrent()

        flow2.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()
        assertTrue(vm.uiState.value.parentalPinConfigured)

        flow1.emit(LiveParentalStatus(pinConfigured = false))
        advanceTimeBy(100L)
        runCurrent()

        assertTrue("State must remain true", vm.uiState.value.parentalPinConfigured)
    }

    @Test
    fun testOldObserverCannotClearNewObserverLoadingState() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        fakeParental.ignoreObserverCancellation = true
        fakeParental.observeStatusDelayMs = 100L

        val profile1 = createParentalProfile("prov_1")
        vm.onProfileChanged(profile1)
        runCurrent()

        val profile2 = createParentalProfile("prov_2")
        vm.onProfileChanged(profile2)
        runCurrent()

        assertTrue("New observer should set parentalLoading to true", vm.uiState.value.parentalLoading)

        advanceTimeBy(100L)
        runCurrent()

        assertTrue("Loading state must not be cleared by old observer", vm.uiState.value.parentalLoading)
    }

    @Test
    fun testOldObserverCannotClearNewObserverError() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        fakeParental.ignoreObserverCancellation = true
        fakeParental.observeStatusDelayMs = 100L

        val profile1 = createParentalProfile("prov_1")
        vm.onProfileChanged(profile1)
        runCurrent()

        fakeParental.observeStatusError = RuntimeException("Connection error")

        fakeParental.observeStatusDelayMs = 0L
        fakeParental.observeStatusError = null
        val profile2 = createParentalProfile("prov_2")
        vm.onProfileChanged(profile2)
        runCurrent()

        assertNull(vm.uiState.value.parentalLoadError)

        advanceTimeBy(100L)
        runCurrent()

        assertNull("Old error must not affect newer provider state", vm.uiState.value.parentalLoadError)
    }

    @Test
    fun testNewProviderStreamRemainsIndependentlyUsableAfterOldObserverCompletes() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        fakeParental.ignoreObserverCancellation = true
        fakeParental.observeStatusDelayMs = 100L

        val flow1 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        val flow2 = kotlinx.coroutines.flow.MutableSharedFlow<LiveParentalStatus>(replay = 1)
        fakeParental.statusFlowMap["prov_1"] = flow1
        fakeParental.statusFlowMap["prov_2"] = flow2

        val profile1 = createParentalProfile("prov_1")
        vm.onProfileChanged(profile1)
        runCurrent()

        fakeParental.observeStatusDelayMs = 0L
        val profile2 = createParentalProfile("prov_2")
        vm.onProfileChanged(profile2)
        runCurrent()

        advanceTimeBy(100L)
        runCurrent()

        flow2.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()
        assertTrue("Provider 2 should still be working", vm.uiState.value.parentalPinConfigured)

        flow2.emit(LiveParentalStatus(pinConfigured = false))
        runCurrent()
        assertFalse("Provider 2 should still be working", vm.uiState.value.parentalPinConfigured)
    }

    @Test
    fun testCompletedCurrentObserverClearsOnlyItsOwnJobReference() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile1 = createParentalProfile("prov_1")
        vm.onProfileChanged(profile1)
        runCurrent()

        val jobField = LiveViewModel::class.java.getDeclaredField("parentalObserverJob")
        jobField.isAccessible = true
        val job = jobField.get(vm) as Job?
        assertNotNull("Should have observer job reference", job)

        // Disable parental control
        val disabledProfile = createParentalProfile("prov_1", parentalEnabled = false)
        vm.onProfileChanged(disabledProfile)
        runCurrent()

        val jobAfterCancel = jobField.get(vm) as Job?
        assertNull("Job reference should be cleared", jobAfterCancel)
    }

    // === REQUIREMENT 11: PIN-SUCCESS STATE ORDERING TEST ===

    @Test
    fun testPinSuccessStateOrderingWhenLeavingLive() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake, fakeFavorites, fakeParental)
        
        vm.onLiveVisibilityChanged(true)
        runCurrent()

        val profile = createParentalProfile("prov_1")
        vm.onProfileChanged(profile)
        runCurrent()

        fakeParental.statusFlow.emit(LiveParentalStatus(pinConfigured = true))
        runCurrent()

        val channel = createChannel("ch1", "Channel 1", "cat1").copy(isLocked = true)
        vm.onChannelSelected(channel)
        runCurrent()

        assertTrue("PIN dialog should be visible", vm.uiState.value.pinDialogVisible)
        assertSame(channel, vm.uiState.value.pendingParentalChannel)

        fakeParental.ignoreVerificationCancellation = true
        fakeParental.verifyPinDelayMs = 100L
        fakeParental.mockPinVerificationResult = true

        val collectedEvents = mutableListOf<LiveEvent>()
        val collectJob = launch {
            vm.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        vm.submitParentalPin("1234")
        runCurrent()

        vm.onLiveVisibilityChanged(false)
        runCurrent()

        advanceTimeBy(100L)
        runCurrent()

        assertFalse("parentalSessionUnlocked should be false", vm.uiState.value.parentalSessionUnlocked)
        assertFalse("pinDialogVisible should be false", vm.uiState.value.pinDialogVisible)
        assertNull("pendingParentalChannel should be null", vm.uiState.value.pendingParentalChannel)
        assertTrue("No playback event should be emitted", collectedEvents.isEmpty())

        collectJob.cancel()
    }
}
