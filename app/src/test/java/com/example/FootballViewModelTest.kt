package com.example

import com.example.config.ProviderProfile
import com.example.data.FootballCompetitionPreference
import com.example.data.FootballMatch
import com.example.data.FootballWatchMatch
import com.example.ui.feature.football.FootballDataSource
import com.example.ui.feature.football.FootballViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class FootballViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val defaultProfile = com.example.config.ProviderConfigRegistry.ALL_PROFILES.first()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    class FakeFootballDataSource : FootballDataSource {
        var mockShowOnHome: Boolean = true
        var mockSelectedCompetitionKeys: List<String> = emptyList()
        var mockHasSeenSetup: Boolean = false
        var mockCachedCompetitions: List<FootballCompetitionPreference> = emptyList()

        // Configurable results
        var competitionsResult: List<FootballCompetitionPreference> = emptyList()
        var scheduleResult: List<FootballWatchMatch> = emptyList()
        var competitionsError: Throwable? = null
        var scheduleError: Throwable? = null

        // Request parameters
        var lastCompetitionsProviderId: String? = null
        var lastCompetitionsForceRefresh: Boolean? = null
        var lastScheduleProviderId: String? = null
        var lastScheduleCompetitionKeys: List<String>? = null

        // Call counters
        var competitionsCallCount = 0
        var scheduleCallCount = 0

        // Configurable delays (in ms)
        var competitionsDelayMs: Long = 0L
        var scheduleDelayMs: Long = 0L

        override fun getShowOnHome(): Boolean = mockShowOnHome
        override fun setShowOnHome(enabled: Boolean) { mockShowOnHome = enabled }

        override fun getSelectedCompetitionKeys(): List<String> = mockSelectedCompetitionKeys
        override fun setSelectedCompetitionKeys(keys: List<String>) { mockSelectedCompetitionKeys = keys }

        override fun hasSeenSetup(): Boolean = mockHasSeenSetup
        override fun setHasSeenSetup(seen: Boolean) { mockHasSeenSetup = seen }

        override fun getCachedCompetitions(): List<FootballCompetitionPreference> = mockCachedCompetitions

        override suspend fun getCompetitions(
            providerId: String,
            forceRefresh: Boolean
        ): List<FootballCompetitionPreference> {
            competitionsCallCount++
            lastCompetitionsProviderId = providerId
            lastCompetitionsForceRefresh = forceRefresh
            if (competitionsDelayMs > 0) {
                kotlinx.coroutines.delay(competitionsDelayMs)
            }
            competitionsError?.let { throw it }
            return competitionsResult
        }

        override suspend fun getSchedule(
            providerId: String,
            competitionKeys: List<String>
        ): List<FootballWatchMatch> {
            scheduleCallCount++
            lastScheduleProviderId = providerId
            lastScheduleCompetitionKeys = competitionKeys
            if (scheduleDelayMs > 0) {
                kotlinx.coroutines.delay(scheduleDelayMs)
            }
            scheduleError?.let { throw it }
            return scheduleResult
        }
    }

    private fun createMockMatch(id: Int, competitionCode: String): FootballMatch {
        return FootballMatch(
            matchId = id,
            competitionCode = competitionCode,
            competitionName = "La Liga",
            competitionEmblemUrl = null,
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            status = "TIMED",
            matchday = 1,
            stage = "REGULAR_SEASON",
            homeTeamId = 1,
            homeTeamName = "Real Madrid",
            homeTeamCrestUrl = null,
            awayTeamId = 2,
            awayTeamName = "FC Barcelona",
            awayTeamCrestUrl = null,
            homeScore = null,
            awayScore = null
        )
    }

    private fun createMockWatchMatch(id: Int, competitionCode: String): FootballWatchMatch {
        return FootballWatchMatch(
            match = createMockMatch(id, competitionCode),
            matchedChannel = null,
            matchedProgramName = null,
            confidence = "NONE"
        )
    }

    // 1. Profile initialization restores saved preferences.
    @Test
    fun testProfileInitializationRestoresPreferences() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockShowOnHome = true
            mockSelectedCompetitionKeys = listOf("PL", "LL")
            mockHasSeenSetup = true
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        val state = vm.uiState.value
        assertTrue(state.featureEnabled)
        assertTrue(state.showOnHome)
        assertEquals(setOf("PL", "LL"), state.selectedCompetitionKeys)
        assertTrue(state.hasSeenSetup)
    }

    // 2. Disabled feature does not request competitions.
    @Test
    fun testDisabledFeatureDoesNotRequestCompetitions() = runTest {
        val fake = FakeFootballDataSource()
        val vm = FootballViewModel(fake)
        val disabledProfile = defaultProfile.copy(
            features = defaultProfile.features.copy(footballScheduleEnabled = false)
        )
        vm.onProfileChanged(disabledProfile)
        val state = vm.uiState.value
        assertFalse(state.featureEnabled)
        assertEquals(0, fake.competitionsCallCount)
    }

    // 3. Disabled feature does not request schedule.
    @Test
    fun testDisabledFeatureDoesNotRequestSchedule() = runTest {
        val fake = FakeFootballDataSource()
        val vm = FootballViewModel(fake)
        val disabledProfile = defaultProfile.copy(
            features = defaultProfile.features.copy(footballScheduleEnabled = false)
        )
        vm.onProfileChanged(disabledProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")
        assertEquals(0, fake.scheduleCallCount)
    }

    // 4. Empty competition keys do not request schedule.
    @Test
    fun testEmptyKeysDoNotRequestSchedule() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = emptyList()
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")
        assertEquals(0, fake.scheduleCallCount)
    }

    // 5. Home invisible does not request schedule.
    @Test
    fun testHomeInvisibleDoesNotRequestSchedule() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("LL")
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(false, "provider_001")
        assertEquals(0, fake.scheduleCallCount)
    }

    // 6. Entering Home requests schedule once.
    @Test
    fun testEnteringHomeRequestsScheduleOnce() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("LL")
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")
        advanceUntilIdle()
        assertEquals(1, fake.scheduleCallCount)
    }

    // 7. Leaving Home cancels an active schedule request.
    @Test
    fun testLeavingHomeCancelsActiveScheduleRequest() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("LL")
            scheduleDelayMs = 1000L
            scheduleResult = listOf(createMockWatchMatch(1, "LL"))
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        advanceTimeBy(500L)
        assertTrue(vm.uiState.value.scheduleLoading)

        vm.onHomeVisibilityChanged(false, "provider_001")

        advanceUntilIdle()
        assertFalse(vm.uiState.value.scheduleLoading)
        assertTrue(vm.uiState.value.matches.isEmpty())
    }

    // 8. Changing selected keys cancels the old request.
    @Test
    fun testChangingSelectedKeysCancelsOldRequest() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleDelayMs = 1000L
            scheduleResult = listOf(createMockWatchMatch(1, "PL"))
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        advanceTimeBy(500L)
        assertEquals(1, fake.scheduleCallCount)

        fake.mockSelectedCompetitionKeys = listOf("LL")
        vm.saveSettingsSelection(setOf("LL"), "provider_001")

        advanceUntilIdle()
        assertEquals(2, fake.scheduleCallCount)
    }

    // 9. Old request result cannot overwrite the new result.
    @Test
    fun testOldRequestResultCannotOverwriteNewResult() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleDelayMs = 1000L
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        advanceTimeBy(500L)

        fake.scheduleResult = listOf(createMockWatchMatch(2, "LL"))
        vm.retrySchedule("provider_001")

        advanceUntilIdle()
        assertEquals(listOf(createMockWatchMatch(2, "LL")), vm.uiState.value.matches)
    }

    // 10. Provider change cancels the old provider request.
    @Test
    fun testProviderChangeCancelsOldProviderRequest() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleDelayMs = 1000L
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        advanceTimeBy(500L)

        val newProfile = defaultProfile.copy(providerId = "provider_002")
        vm.onProfileChanged(newProfile)

        advanceUntilIdle()
        assertTrue(vm.uiState.value.matches.isEmpty())
    }

    // 11. Old provider result cannot update the new provider state.
    @Test
    fun testOldProviderResultCannotUpdateNewProviderState() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleDelayMs = 1000L
            scheduleResult = listOf(createMockWatchMatch(1, "PL"))
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        advanceTimeBy(500L)

        // Clear mock keys so the new provider doesn't trigger a new request on profile change
        fake.mockSelectedCompetitionKeys = emptyList()

        val newProfile = defaultProfile.copy(providerId = "provider_002")
        vm.onProfileChanged(newProfile)

        advanceUntilIdle()
        assertTrue(vm.uiState.value.matches.isEmpty())
    }

    // 12. Duplicate same-key load is prevented.
    @Test
    fun testDuplicateSameKeyLoadIsPrevented() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleDelayMs = 1000L
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        vm.loadSchedule("provider_001", force = false)

        advanceUntilIdle()
        assertEquals(1, fake.scheduleCallCount)
    }

    // 13. Forced retry starts one new request.
    @Test
    fun testForcedRetryStartsOneNewRequest() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")
        advanceUntilIdle()

        assertEquals(1, fake.scheduleCallCount)

        vm.retrySchedule("provider_001")
        advanceUntilIdle()

        assertEquals(2, fake.scheduleCallCount)
    }

    // 14. Timeout sets the timeout message.
    @Test
    fun testTimeoutSetsTimeoutMessage() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleError = try {
                kotlinx.coroutines.withTimeout(0) {}
                null
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                e
            }
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")
        advanceUntilIdle()

        assertEquals("Football schedule request timed out.", vm.uiState.value.scheduleError)
    }

    // 15. General failure sets the safe error.
    @Test
    fun testGeneralFailureSetsSafeError() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleError = RuntimeException("Server error")
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")
        advanceUntilIdle()

        assertEquals("Could not load football schedule.", vm.uiState.value.scheduleError)
    }

    // 16. Normal cancellation does not set an error.
    @Test
    fun testNormalCancellationDoesNotSetError() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleDelayMs = 1000L
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        advanceTimeBy(500L)
        vm.onHomeVisibilityChanged(false, "provider_001")
        advanceUntilIdle()

        assertNull(vm.uiState.value.scheduleError)
    }

    // 17. Loading stops after success.
    @Test
    fun testLoadingStopsAfterSuccess() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        assertTrue(vm.uiState.value.scheduleLoading)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.scheduleLoading)
    }

    // 18. Loading stops after failure.
    @Test
    fun testLoadingStopsAfterFailure() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleError = RuntimeException("Failure")
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        assertTrue(vm.uiState.value.scheduleLoading)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.scheduleLoading)
    }

    // 19. Competition force-refresh cannot be overwritten by the old request.
    @Test
    fun testCompetitionForceRefreshCannotBeOverwrittenByOldRequest() = runTest {
        val fake = FakeFootballDataSource().apply {
            competitionsDelayMs = 1000L
            competitionsResult = listOf(FootballCompetitionPreference("PL", "Premier League"))
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)

        advanceTimeBy(500L)
        assertEquals(1, fake.competitionsCallCount)

        fake.competitionsResult = listOf(FootballCompetitionPreference("LL", "La Liga"))
        vm.loadCompetitions("provider_001", forceRefresh = true)

        advanceUntilIdle()
        assertEquals(listOf(FootballCompetitionPreference("LL", "La Liga")), vm.uiState.value.competitions)
    }

    // 20. Setup save enables Home.
    @Test
    fun testSetupSaveEnablesHome() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockShowOnHome = false
            mockHasSeenSetup = false
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        vm.saveInitialSetupSelection(setOf("PL"), "provider_001")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.showOnHome)
        assertTrue(state.hasSeenSetup)
        assertFalse(state.setupDialogVisible)
        assertEquals(setOf("PL"), state.selectedCompetitionKeys)
        assertEquals(1, fake.scheduleCallCount)
    }

    // 21. Settings save preserves a disabled Home toggle.
    @Test
    fun testSettingsSavePreservesDisabledHomeToggle() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockShowOnHome = false
            mockHasSeenSetup = true
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        vm.saveSettingsSelection(setOf("LL"), "provider_001")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.showOnHome)
        assertEquals(0, fake.scheduleCallCount)
    }

    // 22. Settings cancel does not persist draft keys.
    @Test
    fun testSettingsCancelDoesNotPersistDraftKeys() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)

        vm.openSettingsDialog()
        vm.selectCompetition("LL")

        vm.closeSettingsDialog()

        val state = vm.uiState.value
        assertEquals(setOf("PL"), state.selectedCompetitionKeys)
    }

    // 23. Setup cancel preserves the existing Not-now behavior.
    @Test
    fun testSetupCancelPreservesNotNowBehavior() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockHasSeenSetup = false
            mockShowOnHome = true
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)

        vm.closeSetupDialog()

        val state = vm.uiState.value
        assertTrue(state.hasSeenSetup)
        assertFalse(state.showOnHome)
        assertTrue(state.selectedCompetitionKeys.isEmpty())
    }

    // 24. Feature disable closes both dialogs.
    @Test
    fun testFeatureDisableClosesBothDialogs() = runTest {
        val fake = FakeFootballDataSource()
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)

        vm.openSetupDialog()
        vm.openSettingsDialog()

        val disabledProfile = defaultProfile.copy(
            features = defaultProfile.features.copy(footballScheduleEnabled = false)
        )
        vm.onProfileChanged(disabledProfile)

        val state = vm.uiState.value
        assertFalse(state.setupDialogVisible)
        assertFalse(state.settingsDialogVisible)
    }

    // 25. Feature disable cancels all jobs.
    @Test
    fun testFeatureDisableCancelsAllJobs() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleDelayMs = 1000L
            competitionsDelayMs = 1000L
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        advanceTimeBy(500L)

        val disabledProfile = defaultProfile.copy(
            features = defaultProfile.features.copy(footballScheduleEnabled = false)
        )
        vm.onProfileChanged(disabledProfile)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.scheduleLoading)
        assertFalse(vm.uiState.value.competitionsLoading)
        assertTrue(vm.uiState.value.matches.isEmpty())
    }

    // 26. ViewModel clear cancels active work.
    @Test
    fun testViewModelClearCancelsActiveWork() = runTest {
        val fake = FakeFootballDataSource().apply {
            mockSelectedCompetitionKeys = listOf("PL")
            scheduleDelayMs = 1000L
        }
        val vm = FootballViewModel(fake)
        vm.onProfileChanged(defaultProfile)
        vm.onHomeVisibilityChanged(true, "provider_001")

        advanceTimeBy(500L)

        val onClearedMethod = vm::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.scheduleLoading)
        assertTrue(vm.uiState.value.matches.isEmpty())
    }
}
