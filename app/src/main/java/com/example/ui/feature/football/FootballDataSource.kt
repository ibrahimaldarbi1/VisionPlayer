package com.example.ui.feature.football

import com.example.data.FootballCompetitionPreference
import com.example.data.FootballWatchMatch

interface FootballDataSource {

    fun getShowOnHome(): Boolean

    fun setShowOnHome(enabled: Boolean)

    fun getSelectedCompetitionKeys(): List<String>

    fun setSelectedCompetitionKeys(keys: List<String>)

    fun hasSeenSetup(): Boolean

    fun setHasSeenSetup(seen: Boolean)

    fun getCachedCompetitions(): List<FootballCompetitionPreference>

    suspend fun getCompetitions(
        providerId: String,
        forceRefresh: Boolean
    ): List<FootballCompetitionPreference>

    suspend fun getSchedule(
        providerId: String,
        competitionKeys: List<String>
    ): List<FootballWatchMatch>
}
