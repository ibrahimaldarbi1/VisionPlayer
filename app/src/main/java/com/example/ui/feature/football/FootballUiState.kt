package com.example.ui.feature.football

import com.example.data.FootballCompetitionPreference
import com.example.data.FootballWatchMatch

data class FootballUiState(
    val featureEnabled: Boolean = false,
    val showOnHome: Boolean = true,

    val selectedCompetitionKeys: Set<String> = emptySet(),

    val competitions: List<FootballCompetitionPreference> = emptyList(),

    val matches: List<FootballWatchMatch> = emptyList(),

    val competitionsLoading: Boolean = false,

    val scheduleLoading: Boolean = false,

    val competitionsError: String? = null,

    val scheduleError: String? = null,

    val hasSeenSetup: Boolean = false,

    val setupDialogVisible: Boolean = false,

    val settingsDialogVisible: Boolean = false,

    val retryCount: Int = 0,

    val draftCompetitionKeys: Set<String> = emptySet()
)
