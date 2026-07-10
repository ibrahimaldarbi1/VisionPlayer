package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.feature.football.FootballViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FootballViewModelTest {

    @Test
    fun testInitializationAndVisibility() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        // Ensure fresh prefs
        repository.footballPrefs.showFootballScheduleOnHome = true
        repository.footballPrefs.selectedFootballCompetitionKeys = listOf("LL", "PL")
        repository.footballPrefs.hasSeenFootballCompetitionSetup = true

        val viewModel = FootballViewModel(repository)
        val profile = com.example.config.ProviderConfigRegistry.ALL_PROFILES.first()

        viewModel.initialize(profile)
        ShadowLooper.idleMainLooper()

        val state = viewModel.uiState.value
        assertEquals(true, state.showOnHome)
        assertEquals(setOf("LL", "PL"), state.selectedCompetitionKeys)
        assertEquals(true, state.hasSeenSetup)
    }

    @Test
    fun testFirstLaunchSetupRequired() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        // Simulate first launch
        repository.footballPrefs.showFootballScheduleOnHome = false
        repository.footballPrefs.selectedFootballCompetitionKeys = emptyList()
        repository.footballPrefs.hasSeenFootballCompetitionSetup = false

        val viewModel = FootballViewModel(repository)
        val profile = com.example.config.ProviderConfigRegistry.ALL_PROFILES.first()

        viewModel.initialize(profile)
        ShadowLooper.idleMainLooper()

        val state = viewModel.uiState.value
        assertEquals(false, state.hasSeenSetup)
        assertEquals(true, state.setupDialogVisible)
    }

    @Test
    fun testCompetitionSelectionAndSaving() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        repository.footballPrefs.showFootballScheduleOnHome = true
        repository.footballPrefs.selectedFootballCompetitionKeys = listOf("LL")
        repository.footballPrefs.hasSeenFootballCompetitionSetup = true

        val viewModel = FootballViewModel(repository)
        val profile = com.example.config.ProviderConfigRegistry.ALL_PROFILES.first()

        viewModel.initialize(profile)
        ShadowLooper.idleMainLooper()

        // Open settings dialog, which loads competitions and initializes draft keys
        viewModel.openSettingsDialog()
        ShadowLooper.idleMainLooper()

        var state = viewModel.uiState.value
        assertTrue(state.settingsDialogVisible)
        assertEquals(setOf("LL"), state.draftCompetitionKeys)

        // Select PL
        viewModel.selectCompetition("PL")
        state = viewModel.uiState.value
        assertEquals(setOf("LL", "PL"), state.draftCompetitionKeys)

        // Toggle LL off
        viewModel.selectCompetition("LL")
        state = viewModel.uiState.value
        assertEquals(setOf("PL"), state.draftCompetitionKeys)

        // Save
        viewModel.saveCompetitionSelection(state.draftCompetitionKeys, profile.providerId)
        ShadowLooper.idleMainLooper()

        state = viewModel.uiState.value
        assertFalse(state.settingsDialogVisible)
        assertEquals(setOf("PL"), state.selectedCompetitionKeys)
        assertEquals(listOf("PL"), repository.footballPrefs.selectedFootballCompetitionKeys)
    }

    @Test
    fun testSelectAllAndClearAll() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        val viewModel = FootballViewModel(repository)
        val profile = com.example.config.ProviderConfigRegistry.ALL_PROFILES.first()

        viewModel.initialize(profile)
        ShadowLooper.idleMainLooper()

        viewModel.openSettingsDialog()
        ShadowLooper.idleMainLooper()

        viewModel.selectAllCompetitions()
        var state = viewModel.uiState.value
        // Verify all loaded competitions are in draft keys
        val expectedKeys = state.competitions.map { it.competitionKey }.toSet()
        assertEquals(expectedKeys, state.draftCompetitionKeys)

        viewModel.clearCompetitionSelection()
        state = viewModel.uiState.value
        assertTrue(state.draftCompetitionKeys.isEmpty())
    }
}
