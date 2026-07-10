package com.example.ui.feature.football

import com.example.data.FootballCompetitionPreference
import com.example.data.FootballWatchMatch
import com.example.data.IptvRepository

class RepositoryFootballDataSource(
    private val repository: IptvRepository
) : FootballDataSource {

    override fun getShowOnHome(): Boolean {
        return repository.footballPrefs.showFootballScheduleOnHome
    }

    override fun setShowOnHome(enabled: Boolean) {
        repository.footballPrefs.showFootballScheduleOnHome = enabled
    }

    override fun getSelectedCompetitionKeys(): List<String> {
        return repository.footballPrefs.selectedFootballCompetitionKeys
    }

    override fun setSelectedCompetitionKeys(keys: List<String>) {
        repository.footballPrefs.selectedFootballCompetitionKeys = keys
    }

    override fun hasSeenSetup(): Boolean {
        return repository.footballPrefs.hasSeenFootballCompetitionSetup
    }

    override fun setHasSeenSetup(seen: Boolean) {
        repository.footballPrefs.hasSeenFootballCompetitionSetup = seen
    }

    override fun getCachedCompetitions(): List<FootballCompetitionPreference> {
        return repository.footballPrefs.getCachedCompetitions()
    }

    override suspend fun getCompetitions(
        providerId: String,
        forceRefresh: Boolean
    ): List<FootballCompetitionPreference> {
        return repository.getFootballCompetitions(providerId, forceRefresh)
    }

    override suspend fun getSchedule(
        providerId: String,
        competitionKeys: List<String>
    ): List<FootballWatchMatch> {
        return repository.getFootballSchedule(providerId, competitionKeys)
    }
}
