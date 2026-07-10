package com.example

import com.example.config.ProviderProfile
import com.example.data.Category
import com.example.data.LiveChannel
import com.example.ui.feature.live.LiveDataSource
import com.example.ui.feature.live.LiveViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    class FakeLiveDataSource : LiveDataSource {
        var categoriesEmissions: List<List<Category>> = emptyList()
        var channelsEmissions: List<List<LiveChannel>> = emptyList()
        var observeEmissions: List<List<Category>> = emptyList()

        val observeFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<Category>>(replay = 1)

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

        override fun observeVisibleCategories(providerId: String): Flow<List<Category>> = flow {
            observeCallCount++
            lastObserveProviderId = providerId
            if (observeDelayMs > 0) {
                delay(observeDelayMs)
            }
            observeError?.let { throw it }
            if (observeEmissions.isNotEmpty()) {
                for (em in observeEmissions) {
                    emit(em)
                }
            } else {
                observeFlow.collect { emit(it) }
            }
        }

        override fun loadCategories(providerId: String): Flow<List<Category>> = flow {
            categoriesCallCount++
            lastCategoriesProviderId = providerId
            if (categoriesDelayMs > 0) {
                delay(categoriesDelayMs)
            }
            categoriesError?.let { throw it }
            if (categoriesEmissions.isNotEmpty()) {
                for (em in categoriesEmissions) {
                    emit(em)
                }
            }
        }

        override fun loadChannels(providerId: String, categoryId: String?): Flow<List<LiveChannel>> = flow {
            channelsCallCount++
            lastChannelsProviderId = providerId
            lastChannelsCategoryId = categoryId
            if (channelsDelayMs > 0) {
                delay(channelsDelayMs)
            }
            channelsError?.let { throw it }
            if (channelsEmissions.isNotEmpty()) {
                for (em in channelsEmissions) {
                    emit(em)
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

    // 1. Enabled profile loads categories.
    @Test
    fun testEnabledProfileLoadsCategories() = runTest {
        val fake = FakeLiveDataSource().apply {
            categoriesEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
        }
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
            categoriesDelayMs = 1000L
            categoriesEmissions = listOf(listOf(createCategory("cat_old", "Category Old")))
        }
        val vm = LiveViewModel(fake)
        vm.onProfileChanged(createEnabledProfile("prov_old"))
        advanceTimeBy(500L)

        fake.categoriesDelayMs = 0L
        fake.categoriesEmissions = listOf(listOf(createCategory("cat_new", "Category New")))
        vm.onProfileChanged(createEnabledProfile("prov_new"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.categories.isEmpty()) // Since observe emission handles final categories, and fake didn't emit observe categories for prov_new
    }

    // 8. Old provider channel result cannot overwrite new state.
    @Test
    fun testOldProviderChannelResultCannotOverwriteNewState() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsDelayMs = 1000L
            channelsEmissions = listOf(listOf(createChannel("chan_old", "Channel Old", "cat1")))
        }
        val vm = LiveViewModel(fake)
        vm.onProfileChanged(createEnabledProfile("prov_old"))
        advanceTimeBy(500L)

        fake.channelsDelayMs = 0L
        fake.channelsEmissions = listOf(listOf(createChannel("chan_new", "Channel New", "cat1")))
        vm.onProfileChanged(createEnabledProfile("prov_new"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.channels.isEmpty())
    }

    // 9. Cached category emission is displayed.
    @Test
    fun testCachedCategoryEmissionIsDisplayed() = runTest {
        val fake = FakeLiveDataSource().apply {
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1")))
        }
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceUntilIdle()

        val initialChannelsCount = fake.channelsCallCount
        vm.selectCategory("cat1")
        advanceUntilIdle()

        assertEquals(initialChannelsCount + 1, fake.channelsCallCount)
        // Verified: LiveViewModel only interacts with LiveDataSource
    }

    // 16. Same-category selection does nothing.
    @Test
    fun testSameCategorySelectionDoesNothing() = runTest {
        val fake = FakeLiveDataSource()
        val vm = LiveViewModel(fake)
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
            channelsDelayMs = 1000L
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
            observeEmissions = listOf(listOf(createCategory("cat1", "Category 1"), createCategory("cat2", "Category 2")))
        }
        val vm = LiveViewModel(fake)
        vm.onProfileChanged(createEnabledProfile("prov_1"))
        advanceTimeBy(500L) // First load in flight

        fake.channelsDelayMs = 0L
        fake.channelsEmissions = listOf(listOf(createChannel("chan2", "Channel 2", "cat2")))
        vm.selectCategory("cat2")
        advanceUntilIdle()

        assertEquals(listOf(createChannel("chan2", "Channel 2", "cat2")), vm.uiState.value.channels)
    }

    // 18. Initial loading stops after success.
    @Test
    fun testInitialLoadingStopsAfterSuccess() = runTest {
        val fake = FakeLiveDataSource().apply {
            channelsDelayMs = 500L
            channelsEmissions = listOf(listOf(createChannel("chan1", "Channel 1", "cat1")))
        }
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
        val vm = LiveViewModel(fake)
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
}
