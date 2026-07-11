package com.example.ui.feature.epg

import com.example.data.EpgProgramEntity
import com.example.data.LiveChannel

data class EpgUiState(
    val featureEnabled: Boolean = false,
    val guideVisible: Boolean = false,
    val channels: List<LiveChannel> = emptyList(),
    val selectedChannelId: String? = null,
    val programs: List<EpgProgramEntity> = emptyList(),
    val programsLoading: Boolean = false,
    val programsRefreshing: Boolean = false,
    val programsError: String? = null
) {
    val selectedChannel: LiveChannel?
        get() = channels.firstOrNull { it.id == selectedChannelId }
}
