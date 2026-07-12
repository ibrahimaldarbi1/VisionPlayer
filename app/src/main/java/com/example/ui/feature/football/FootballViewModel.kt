package com.example.ui.feature.football

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.FootballCompetitionPreference
import com.example.data.FootballWatchMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class FootballViewModel(
    private val dataSource: FootballDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(FootballUiState())
    val uiState: StateFlow<FootballUiState> = _uiState.asStateFlow()

    private var currentProfile: ProviderProfile? = null
    private var isHomeVisible: Boolean = false
    private var currentProviderId: String? = null

    private var activeCompetitionJob: Job? = null
    private var activeScheduleJob: Job? = null

    private var activeScheduleKey: ScheduleRequestKey? = null
    private var scheduleGeneration: Long = 0L

    private var competitionGeneration: Long = 0L
    private var activeCompetitionProviderId: String? = null

    private data class ScheduleRequestKey(
        val providerId: String,
        val competitionKeys: List<String>
    )

    fun initialize(profile: ProviderProfile) {
        onProfileChanged(profile)
    }

    fun onProfileChanged(profile: ProviderProfile) {
        currentProfile = profile
        val isEnabled = com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldShowFootball(profile.features)
        val providerId = profile.providerId

        if (!isEnabled) {
            currentProviderId = providerId
            cancelCompetitionRequest(clearLoading = true)
            cancelScheduleRequest(clearLoading = true)
            _uiState.update {
                it.copy(
                    featureEnabled = false,
                    matches = emptyList(),
                    competitionsLoading = false,
                    scheduleLoading = false,
                    setupDialogVisible = false,
                    settingsDialogVisible = false,
                    competitionsError = null,
                    scheduleError = null,
                    selectedCompetitionKeys = emptySet(),
                    draftCompetitionKeys = emptySet(),
                    hasSeenSetup = false
                )
            }
            return
        }

        val providerChanged = (currentProviderId != providerId)
        currentProviderId = providerId

        if (providerChanged) {
            cancelCompetitionRequest(clearLoading = true)
            cancelScheduleRequest(clearLoading = true)
            _uiState.update {
                it.copy(
                    matches = emptyList(),
                    competitionsError = null,
                    scheduleError = null,
                    competitionsLoading = false,
                    scheduleLoading = false
                )
            }
        }

        val savedShowOnHome = dataSource.getShowOnHome()
        val savedKeys = dataSource.getSelectedCompetitionKeys().toSet()
        val savedHasSeenSetup = dataSource.hasSeenSetup()
        val cachedComps = dataSource.getCachedCompetitions()

        _uiState.update { currentState ->
            currentState.copy(
                featureEnabled = true,
                showOnHome = savedShowOnHome,
                selectedCompetitionKeys = savedKeys,
                hasSeenSetup = savedHasSeenSetup,
                setupDialogVisible = if (!savedHasSeenSetup) true else currentState.setupDialogVisible,
                competitions = cachedComps
            )
        }

        loadCompetitions(providerId, forceRefresh = false)

        if (isHomeVisible && savedShowOnHome && savedKeys.isNotEmpty()) {
            loadSchedule(providerId, force = false)
        }
    }

    fun onHomeVisibilityChanged(visible: Boolean, providerId: String) {
        isHomeVisible = visible

        if (!visible) {
            cancelScheduleRequest(clearLoading = true)
            return
        }

        val state = _uiState.value
        if (state.featureEnabled && state.showOnHome && state.selectedCompetitionKeys.isNotEmpty()) {
            loadSchedule(providerId, force = false)
        }
    }

    fun onScheduleCriteriaChanged(providerId: String) {
        val state = _uiState.value
        if (!state.featureEnabled) {
            cancelScheduleRequest(clearLoading = true)
            _uiState.update { it.copy(matches = emptyList()) }
            return
        }
        if (!state.showOnHome) {
            cancelScheduleRequest(clearLoading = true)
            _uiState.update { it.copy(matches = emptyList()) }
            return
        }
        if (state.selectedCompetitionKeys.isEmpty()) {
            cancelScheduleRequest(clearLoading = true)
            _uiState.update { it.copy(matches = emptyList()) }
            return
        }
        if (!isHomeVisible) {
            cancelScheduleRequest(clearLoading = true)
            return
        }
        loadSchedule(providerId, force = false)
    }

    fun loadCompetitions(providerId: String, forceRefresh: Boolean = false) {
        if (activeCompetitionJob?.isActive == true && !forceRefresh && activeCompetitionProviderId == providerId) {
            return
        }

        if (forceRefresh || activeCompetitionProviderId != providerId) {
            cancelCompetitionRequest(clearLoading = false)
        }

        activeCompetitionProviderId = providerId
        competitionGeneration++
        val requestGeneration = competitionGeneration

        val cached = dataSource.getCachedCompetitions()
        val showLoading = cached.isEmpty()

        _uiState.update {
            it.copy(
                competitions = cached,
                competitionsLoading = showLoading,
                competitionsError = null
            )
        }

        activeCompetitionJob = viewModelScope.launch {
            try {
                val freshComps = dataSource.getCompetitions(providerId, forceRefresh)
                val profile = currentProfile
                if (profile == null || !com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldShowFootball(profile.features)) return@launch
                if (requestGeneration == competitionGeneration) {
                    _uiState.update {
                        it.copy(
                            competitions = freshComps.ifEmpty { cached }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration == competitionGeneration) {
                    _uiState.update {
                        if (it.competitions.isEmpty()) {
                            it.copy(competitionsError = "Could not load football competitions.")
                        } else {
                            it
                        }
                    }
                }
            } finally {
                if (requestGeneration == competitionGeneration) {
                    _uiState.update {
                        it.copy(competitionsLoading = false)
                    }
                    activeCompetitionProviderId = null
                }
            }
        }
    }

    fun loadSchedule(providerId: String, force: Boolean = false) {
        val state = _uiState.value
        if (!state.featureEnabled || !state.showOnHome || state.selectedCompetitionKeys.isEmpty() || !isHomeVisible) {
            return
        }

        val normalizedKeys = state.selectedCompetitionKeys.toList().sorted()
        val requestKey = ScheduleRequestKey(providerId, normalizedKeys)

        if (activeScheduleKey == requestKey && _uiState.value.scheduleLoading && !force) {
            return
        }

        cancelScheduleRequest(clearLoading = false)

        activeScheduleKey = requestKey
        scheduleGeneration++
        val requestGeneration = scheduleGeneration

        _uiState.update {
            it.copy(
                scheduleLoading = true,
                scheduleError = null
            )
        }

        activeScheduleJob = viewModelScope.launch {
            val loadId = android.os.SystemClock.elapsedRealtime()
            val retryCount = _uiState.value.retryCount

            android.util.Log.d(
                "FootballTrace",
                "UI_START id=$loadId " +
                    "competitions=${normalizedKeys.size} " +
                    "retry=$retryCount"
            )

            try {
                val matches = dataSource.getSchedule(providerId, normalizedKeys)
                val profile = currentProfile
                if (profile == null || !com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldShowFootball(profile.features)) return@launch
                if (requestGeneration == scheduleGeneration) {
                    _uiState.update {
                        it.copy(
                            matches = matches,
                            scheduleError = null
                        )
                    }
                    android.util.Log.d(
                        "FootballTrace",
                        "UI_SUCCESS id=$loadId " +
                            "matches=${matches.size}"
                    )
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e(
                    "FootballTrace",
                    "UI_TIMEOUT id=$loadId"
                )
                if (requestGeneration == scheduleGeneration) {
                    _uiState.update {
                        it.copy(
                            matches = emptyList(),
                            scheduleError = "Football schedule request timed out."
                        )
                    }
                }
            } catch (e: CancellationException) {
                android.util.Log.d(
                    "FootballTrace",
                    "UI_CANCELLED id=$loadId"
                )
                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "FootballTrace",
                    "UI_ERROR id=$loadId"
                )
                if (requestGeneration == scheduleGeneration) {
                    _uiState.update {
                        it.copy(
                            matches = emptyList(),
                            scheduleError = "Could not load football schedule."
                        )
                    }
                }
            } finally {
                if (requestGeneration == scheduleGeneration) {
                    _uiState.update {
                        it.copy(scheduleLoading = false)
                    }
                    activeScheduleKey = null
                }
                android.util.Log.d(
                    "FootballTrace",
                    "UI_FINALLY id=$loadId loading=false"
                )
            }
        }
    }

    fun retrySchedule(providerId: String) {
        _uiState.update {
            it.copy(retryCount = it.retryCount + 1)
        }
        loadSchedule(providerId, force = true)
    }

    private fun cancelScheduleRequest(clearLoading: Boolean) {
        scheduleGeneration++
        activeScheduleJob?.cancel()
        activeScheduleJob = null
        activeScheduleKey = null
        if (clearLoading) {
            _uiState.update {
                it.copy(scheduleLoading = false)
            }
        }
    }

    private fun cancelCompetitionRequest(clearLoading: Boolean) {
        competitionGeneration++
        activeCompetitionJob?.cancel()
        activeCompetitionJob = null
        activeCompetitionProviderId = null
        if (clearLoading) {
            _uiState.update {
                it.copy(competitionsLoading = false)
            }
        }
    }

    fun saveInitialSetupSelection(selectedKeys: Set<String>, providerId: String) {
        if (!_uiState.value.featureEnabled) return
        val normalized = selectedKeys.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (normalized.isEmpty()) {
            return
        }

        dataSource.setSelectedCompetitionKeys(normalized.toList())
        dataSource.setHasSeenSetup(true)
        dataSource.setShowOnHome(true)

        _uiState.update { currentState ->
            currentState.copy(
                selectedCompetitionKeys = normalized,
                hasSeenSetup = true,
                showOnHome = true,
                setupDialogVisible = false
            )
        }

        if (isHomeVisible) {
            loadSchedule(providerId, force = false)
        }
    }

    fun saveSettingsSelection(selectedKeys: Set<String>, providerId: String) {
        if (!_uiState.value.featureEnabled) return
        val normalized = selectedKeys.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (normalized.isEmpty()) {
            return
        }

        dataSource.setSelectedCompetitionKeys(normalized.toList())
        val currentShowOnHome = dataSource.getShowOnHome()

        _uiState.update { currentState ->
            currentState.copy(
                selectedCompetitionKeys = normalized,
                settingsDialogVisible = false
            )
        }

        if (isHomeVisible && currentShowOnHome) {
            loadSchedule(providerId, force = false)
        }
    }

    fun setShowOnHome(enabled: Boolean, providerId: String) {
        if (!_uiState.value.featureEnabled) return
        dataSource.setShowOnHome(enabled)
        _uiState.update { currentState ->
            currentState.copy(
                showOnHome = enabled
            )
        }
        onScheduleCriteriaChanged(providerId)
    }

    fun openSetupDialog() {
        if (!_uiState.value.featureEnabled) return
        _uiState.update { currentState ->
            currentState.copy(
                setupDialogVisible = true,
                draftCompetitionKeys = currentState.selectedCompetitionKeys
            )
        }
    }

    fun closeSetupDialog() {
        val hasSeenSetup = dataSource.hasSeenSetup()
        if (!hasSeenSetup) {
            dataSource.setShowOnHome(false)
            dataSource.setHasSeenSetup(true)
            _uiState.update { currentState ->
                currentState.copy(
                    hasSeenSetup = true,
                    showOnHome = false,
                    setupDialogVisible = false,
                    selectedCompetitionKeys = emptySet()
                )
            }
        } else {
            _uiState.update { currentState ->
                currentState.copy(
                    setupDialogVisible = false
                )
            }
        }
    }

    fun openSettingsDialog() {
        if (!_uiState.value.featureEnabled) return
        _uiState.update { currentState ->
            currentState.copy(
                settingsDialogVisible = true,
                draftCompetitionKeys = currentState.selectedCompetitionKeys
            )
        }
    }

    fun closeSettingsDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                settingsDialogVisible = false
            )
        }
    }

    fun selectCompetition(key: String) {
        if (!_uiState.value.featureEnabled) return
        _uiState.update { currentState ->
            val newDraft = if (currentState.draftCompetitionKeys.contains(key)) {
                currentState.draftCompetitionKeys - key
            } else {
                currentState.draftCompetitionKeys + key
            }
            currentState.copy(draftCompetitionKeys = newDraft)
        }
    }

    fun clearCompetitionSelection() {
        _uiState.update { currentState ->
            currentState.copy(draftCompetitionKeys = emptySet())
        }
    }

    fun selectAllCompetitions() {
        if (!_uiState.value.featureEnabled) return
        _uiState.update { currentState ->
            val allKeys = currentState.competitions.map { it.competitionKey }.toSet()
            currentState.copy(draftCompetitionKeys = allKeys)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelCompetitionRequest(clearLoading = true)
        cancelScheduleRequest(clearLoading = true)
    }
}
