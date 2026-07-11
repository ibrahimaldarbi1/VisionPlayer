package com.example.ui.feature.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LiveChannel
import com.example.config.ProviderProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EpgViewModel(
    private val dataSource: EpgDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpgUiState())
    val uiState = _uiState.asStateFlow()

    private var currentProviderId: String? = null
    private var programObserverJob: Job? = null
    private var programGeneration: Long = 0L

    fun onProfileChanged(profile: ProviderProfile) {
        val featureEnabled = profile.features.liveTvEnabled && profile.features.epgEnabled

        if (!featureEnabled) {
            cancelProgramObserver(invalidate = true, clearLoading = true)
            _uiState.update {
                it.copy(
                    featureEnabled = false,
                    channels = emptyList(),
                    selectedChannelId = null,
                    programs = emptyList(),
                    programsError = null
                )
            }
            return
        }

        if (currentProviderId != profile.providerId) {
            cancelProgramObserver(invalidate = true, clearLoading = true)
            currentProviderId = profile.providerId
            _uiState.update {
                it.copy(
                    featureEnabled = true,
                    channels = emptyList(),
                    selectedChannelId = null,
                    programs = emptyList(),
                    programsError = null
                )
            }
            return
        }

        _uiState.update { it.copy(featureEnabled = true) }
    }

    fun onChannelsChanged(providerId: String, channels: List<LiveChannel>) {
        val state = _uiState.value
        if (!state.featureEnabled || currentProviderId != providerId) return

        val normalizedChannels = channels.distinctBy { it.id }
        
        if (normalizedChannels.isEmpty()) {
            cancelProgramObserver(invalidate = true, clearLoading = true)
            _uiState.update {
                it.copy(
                    channels = emptyList(),
                    selectedChannelId = null,
                    programs = emptyList(),
                    programsError = null
                )
            }
            return
        }

        val oldSelectedId = state.selectedChannelId
        val oldSelectedChannel = state.selectedChannel
        
        val newSelectedChannel = normalizedChannels.firstOrNull { it.id == oldSelectedId }
        
        if (newSelectedChannel != null) {
            _uiState.update { it.copy(channels = normalizedChannels) }
            val oldKeys = oldSelectedChannel?.let { EpgChannelLookupKeys.forChannel(it) } ?: emptyList()
            val newKeys = EpgChannelLookupKeys.forChannel(newSelectedChannel)
            if (oldKeys != newKeys) {
                observeSelectedChannel(preservePrograms = true)
            }
        } else {
            cancelProgramObserver(invalidate = true, clearLoading = false)
            val firstChannel = normalizedChannels.first()
            _uiState.update {
                it.copy(
                    channels = normalizedChannels,
                    selectedChannelId = firstChannel.id,
                    programs = emptyList(),
                    programsError = null
                )
            }
            if (_uiState.value.guideVisible) {
                observeSelectedChannel(preservePrograms = false)
            }
        }
    }

    fun onGuideVisibilityChanged(visible: Boolean) {
        if (!visible) {
            _uiState.update { it.copy(guideVisible = false) }
            cancelProgramObserver(invalidate = true, clearLoading = true)
        } else {
            val wasVisible = _uiState.value.guideVisible
            _uiState.update { it.copy(guideVisible = true) }
            if (!wasVisible && _uiState.value.featureEnabled && _uiState.value.selectedChannelId != null && programObserverJob == null) {
                observeSelectedChannel(preservePrograms = true)
            }
        }
    }

    fun selectChannel(channelId: String) {
        val state = _uiState.value
        if (!state.featureEnabled) return
        if (state.channels.none { it.id == channelId }) return
        if (state.selectedChannelId == channelId) return

        cancelProgramObserver(invalidate = true, clearLoading = false)
        _uiState.update {
            it.copy(
                selectedChannelId = channelId,
                programs = emptyList(),
                programsError = null
            )
        }
        
        if (_uiState.value.guideVisible) {
            observeSelectedChannel(preservePrograms = false)
        }
    }

    private fun observeSelectedChannel(preservePrograms: Boolean) {
        val state = _uiState.value
        if (!state.featureEnabled || !state.guideVisible) return
        val providerId = currentProviderId ?: return
        val channel = state.selectedChannel ?: return

        cancelProgramObserver(invalidate = true, clearLoading = false)
        
        if (preservePrograms && state.programs.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    programsLoading = false,
                    programsRefreshing = true,
                    programsError = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    programs = emptyList(),
                    programsLoading = true,
                    programsRefreshing = false,
                    programsError = null
                )
            }
        }

        val lookupKeys = EpgChannelLookupKeys.forChannel(channel)
        val capturedGeneration = programGeneration

        lateinit var observerJob: Job
        observerJob = viewModelScope.launch {
            try {
                dataSource.observePrograms(providerId, lookupKeys).collect { emittedPrograms ->
                    val currentState = _uiState.value
                    if (currentProviderId == providerId &&
                        programGeneration == capturedGeneration &&
                        currentState.selectedChannelId == channel.id &&
                        currentState.featureEnabled &&
                        currentState.guideVisible
                    ) {
                        _uiState.update {
                            it.copy(
                                programs = emittedPrograms,
                                programsLoading = false,
                                programsRefreshing = false,
                                programsError = null
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentState = _uiState.value
                if (currentProviderId == providerId &&
                    programGeneration == capturedGeneration &&
                    currentState.selectedChannelId == channel.id &&
                    currentState.featureEnabled &&
                    currentState.guideVisible
                ) {
                    _uiState.update {
                        it.copy(
                            programsLoading = false,
                            programsRefreshing = false,
                            programsError = "Could not load guide information."
                        )
                    }
                }
            } finally {
                if (programObserverJob === observerJob) {
                    programObserverJob = null
                }
            }
        }
        programObserverJob = observerJob
    }

    fun retryPrograms() {
        val state = _uiState.value
        if (currentProviderId == null || state.selectedChannelId == null) return
        observeSelectedChannel(preservePrograms = true)
    }

    fun dismissProgramsError() {
        _uiState.update { it.copy(programsError = null) }
    }

    private fun cancelProgramObserver(invalidate: Boolean, clearLoading: Boolean) {
        if (invalidate) {
            programGeneration++
        }
        val jobToCancel = programObserverJob
        programObserverJob = null
        jobToCancel?.cancel()
        
        if (clearLoading) {
            _uiState.update {
                it.copy(
                    programsLoading = false,
                    programsRefreshing = false
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelProgramObserver(invalidate = true, clearLoading = false)
    }
}
