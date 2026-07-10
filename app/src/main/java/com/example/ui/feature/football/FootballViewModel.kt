package com.example.ui.feature.football

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.IptvRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class FootballViewModel(
    private val repository: IptvRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FootballUiState())
    val uiState: StateFlow<FootballUiState> = _uiState.asStateFlow()

    private var activeCompetitionJob: Job? = null
    private var activeScheduleJob: Job? = null

    fun initialize(profile: com.example.config.ProviderProfile) {
        val isEnabled = profile.features.footballScheduleEnabled
        val showOnHome = repository.footballPrefs.showFootballScheduleOnHome
        val keys = repository.footballPrefs.selectedFootballCompetitionKeys.toSet()
        val hasSeenSetup = repository.footballPrefs.hasSeenFootballCompetitionSetup

        _uiState.update { currentState ->
            if (!isEnabled) {
                currentState.copy(
                    featureEnabled = false,
                    matches = emptyList(),
                    competitionsLoading = false,
                    scheduleLoading = false,
                    setupDialogVisible = false,
                    settingsDialogVisible = false,
                    competitionsError = null,
                    scheduleError = null
                )
            } else {
                currentState.copy(
                    featureEnabled = true,
                    showOnHome = showOnHome,
                    selectedCompetitionKeys = keys,
                    hasSeenSetup = hasSeenSetup,
                    setupDialogVisible = if (!hasSeenSetup) true else currentState.setupDialogVisible,
                    competitions = repository.footballPrefs.getCachedCompetitions()
                )
            }
        }

        if (isEnabled) {
            loadCompetitions(profile.providerId, forceRefresh = false)
        }
    }

    fun onProfileChanged(profile: com.example.config.ProviderProfile) {
        val isEnabled = profile.features.footballScheduleEnabled
        if (!isEnabled) {
            activeCompetitionJob?.cancel()
            activeCompetitionJob = null
            activeScheduleJob?.cancel()
            activeScheduleJob = null

            _uiState.update {
                it.copy(
                    featureEnabled = false,
                    matches = emptyList(),
                    competitionsLoading = false,
                    scheduleLoading = false,
                    setupDialogVisible = false,
                    settingsDialogVisible = false,
                    competitionsError = null,
                    scheduleError = null
                )
            }
        } else {
            val showOnHome = repository.footballPrefs.showFootballScheduleOnHome
            val keys = repository.footballPrefs.selectedFootballCompetitionKeys.toSet()
            val hasSeenSetup = repository.footballPrefs.hasSeenFootballCompetitionSetup

            _uiState.update { currentState ->
                currentState.copy(
                    featureEnabled = true,
                    showOnHome = showOnHome,
                    selectedCompetitionKeys = keys,
                    hasSeenSetup = hasSeenSetup,
                    setupDialogVisible = if (!hasSeenSetup) true else currentState.setupDialogVisible,
                    competitions = repository.footballPrefs.getCachedCompetitions()
                )
            }
        }
    }

    fun loadCompetitions(providerId: String, forceRefresh: Boolean = false) {
        if (activeCompetitionJob?.isActive == true && !forceRefresh) {
            return
        }

        activeCompetitionJob?.cancel()
        activeCompetitionJob = viewModelScope.launch {
            val cached = repository.footballPrefs.getCachedCompetitions()
            val showLoading = cached.isEmpty()

            _uiState.update {
                it.copy(
                    competitions = cached,
                    competitionsLoading = showLoading,
                    competitionsError = null
                )
            }

            try {
                val freshComps = repository.getFootballCompetitions(providerId, forceRefresh)
                _uiState.update {
                    it.copy(
                        competitions = freshComps.ifEmpty { cached }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    if (it.competitions.isEmpty()) {
                        it.copy(competitionsError = "Could not load football competitions.")
                    } else {
                        it
                    }
                }
            } finally {
                _uiState.update {
                    it.copy(competitionsLoading = false)
                }
            }
        }
    }

    fun loadSchedule(providerId: String) {
        val state = _uiState.value
        if (!state.featureEnabled) {
            _uiState.update {
                it.copy(
                    matches = emptyList(),
                    scheduleLoading = false,
                    scheduleError = null
                )
            }
            return
        }

        if (!state.showOnHome) {
            _uiState.update {
                it.copy(
                    matches = emptyList(),
                    scheduleLoading = false,
                    scheduleError = null
                )
            }
            return
        }

        if (state.selectedCompetitionKeys.isEmpty()) {
            _uiState.update {
                it.copy(
                    matches = emptyList(),
                    scheduleLoading = false,
                    scheduleError = null
                )
            }
            return
        }

        if (activeScheduleJob?.isActive == true) {
            return
        }

        activeScheduleJob = viewModelScope.launch {
            val loadId = android.os.SystemClock.elapsedRealtime()
            val retryCount = _uiState.value.retryCount

            android.util.Log.d(
                "FootballTrace",
                "UI_START id=$loadId " +
                    "competitions=${state.selectedCompetitionKeys.size} " +
                    "retry=$retryCount"
            )

            _uiState.update {
                it.copy(
                    scheduleLoading = true,
                    scheduleError = null
                )
            }

            try {
                val matches = repository.getFootballSchedule(
                    providerId = providerId,
                    selectedCompetitionKeys = state.selectedCompetitionKeys.toList()
                )

                _uiState.update {
                    it.copy(
                        matches = matches
                    )
                }

                android.util.Log.d(
                    "FootballTrace",
                    "UI_SUCCESS id=$loadId " +
                        "matches=${matches.size}"
                )
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e(
                    "FootballTrace",
                    "UI_TIMEOUT id=$loadId",
                    e
                )
                _uiState.update {
                    it.copy(
                        matches = emptyList(),
                        scheduleError = "Football schedule request timed out."
                    )
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
                    "UI_ERROR id=$loadId",
                    e
                )
                _uiState.update {
                    it.copy(
                        matches = emptyList(),
                        scheduleError = "Could not load football schedule."
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(scheduleLoading = false)
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
        loadSchedule(providerId)
    }

    fun saveCompetitionSelection(selectedKeys: Set<String>, providerId: String) {
        val normalized = selectedKeys.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (normalized.isEmpty()) {
            return
        }

        repository.footballPrefs.showFootballScheduleOnHome = true
        repository.footballPrefs.selectedFootballCompetitionKeys = normalized.toList()
        repository.footballPrefs.hasSeenFootballCompetitionSetup = true

        _uiState.update { currentState ->
            currentState.copy(
                selectedCompetitionKeys = normalized,
                hasSeenSetup = true,
                setupDialogVisible = false,
                settingsDialogVisible = false,
                showOnHome = true
            )
        }

        loadSchedule(providerId)
    }

    fun setShowOnHome(enabled: Boolean, providerId: String) {
        repository.footballPrefs.showFootballScheduleOnHome = enabled
        _uiState.update { currentState ->
            currentState.copy(
                showOnHome = enabled
            )
        }
        loadSchedule(providerId)
    }

    fun openSetupDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                setupDialogVisible = true,
                draftCompetitionKeys = currentState.selectedCompetitionKeys
            )
        }
    }

    fun closeSetupDialog() {
        val hasSeenSetup = repository.footballPrefs.hasSeenFootballCompetitionSetup
        if (!hasSeenSetup) {
            repository.footballPrefs.showFootballScheduleOnHome = false
            repository.footballPrefs.hasSeenFootballCompetitionSetup = true
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
        _uiState.update { currentState ->
            val allKeys = currentState.competitions.map { it.competitionKey }.toSet()
            currentState.copy(draftCompetitionKeys = allKeys)
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeCompetitionJob?.cancel()
        activeScheduleJob?.cancel()
    }
}
