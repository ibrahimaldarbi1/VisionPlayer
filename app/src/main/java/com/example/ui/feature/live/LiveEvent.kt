package com.example.ui.feature.live

import com.example.data.LiveChannel

sealed interface LiveEvent {

    data class PlayChannel(
        val channel: LiveChannel
    ) : LiveEvent
}
