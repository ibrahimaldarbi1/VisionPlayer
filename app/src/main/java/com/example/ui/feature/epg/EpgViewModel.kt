package com.example.ui.feature.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.LiveChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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

    private var observedProviderId: String? = null
    private var observedChannelId: String? = null
    private var observedLookupKeys: List<String> = emptyList()

    private fun isObserving(
        providerId: String,
        channelId: String,
        lookupKeys: List<String>
    ): Boolean {
        return programObserverJob?.isActive == true &&
                observedProviderId == providerId &&
                observedChannelId == channelId &&
                observedLookupKeys == lookupKeys
    }

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
                    programsLoading = false,
                    programsRefreshing = false,
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
                    programsLoading = false,
                    programsRefreshing = false,
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
                // Channel still exists but keys changed. Refresh.
                observeSelectedChannel(preservePrograms = true)
            }
        } else {
            // Selected channel removed
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
            _uiState.update { it.copy(guideVisible = true) }
            val state = _uiState.value
            if (state.featureEnabled && state.selectedChannelId != null) {
                val providerId = currentProviderId ?: return
                val channel = state.selectedChannel ?: return
                val lookupKeys = EpgChannelLookupKeys.forChannel(channel)
                if (!isObserving(providerId, channel.id, lookupKeys)) {
                    observeSelectedChannel(preservePrograms = true)
                }
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
        
        val capturedGeneration = programGeneration

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

        lateinit var observerJob: Job

        observerJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
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
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
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
                    observedProviderId = null
                    observedChannelId = null
                    observedLookupKeys = emptyList()
                }
            }
        }

        programObserverJob = observerJob
        observedProviderId = providerId
        observedChannelId = channel.id
        observedLookupKeys = lookupKeys

        observerJob.start()
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
        observedProviderId = null
        observedChannelId = null
        observedLookupKeys = emptyList()

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
