package com.example.ui.feature.multiview

import androidx.lifecycle.ViewModel
import com.example.config.ProviderProfile
import com.example.data.LiveChannel
import com.example.ui.feature.shell.FeatureAvailabilityPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MultiViewUiState(
    val activeMultiViewChannels: List<LiveChannel>? = null,
    val showMultiViewSetup: Boolean = false,
    val pendingMultiViewChannels: List<LiveChannel> = emptyList()
)

class MultiViewViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MultiViewUiState())
    val uiState: StateFlow<MultiViewUiState> = _uiState.asStateFlow()

    fun onProfileChanged(profile: ProviderProfile) {
        val multiViewEnabled = FeatureAvailabilityPolicy.shouldShowMultiView(profile.features)
        if (!multiViewEnabled) {
            clearMultiView()
        }
    }

    fun clearMultiView() {
        _uiState.update {
            it.copy(
                activeMultiViewChannels = null,
                showMultiViewSetup = false,
                pendingMultiViewChannels = emptyList()
            )
        }
    }

    fun launchMultiView(selected: List<LiveChannel>) {
        _uiState.update {
            it.copy(
                activeMultiViewChannels = selected,
                showMultiViewSetup = false
            )
        }
    }

    fun showSetup(show: Boolean) {
        _uiState.update { it.copy(showMultiViewSetup = show) }
    }

    fun addToPending(channel: LiveChannel) {
        _uiState.update { state ->
            val updatedPending = if (state.pendingMultiViewChannels.none { it.id == channel.id }) {
                state.pendingMultiViewChannels + channel
            } else {
                state.pendingMultiViewChannels
            }
            state.copy(
                pendingMultiViewChannels = updatedPending,
                showMultiViewSetup = true
            )
        }
    }

    fun setPendingChannels(channels: List<LiveChannel>) {
        _uiState.update { it.copy(pendingMultiViewChannels = channels) }
    }

    fun updateActiveChannels(channels: List<LiveChannel>?) {
        _uiState.update { it.copy(activeMultiViewChannels = channels) }
    }
}
