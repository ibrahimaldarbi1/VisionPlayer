package com.example.ui.feature.epg

import com.example.data.LiveChannel

internal object EpgChannelLookupKeys {

    fun forChannel(channel: LiveChannel): List<String> {
        return listOf(
            channel.epgId,
            channel.id
        )
            .map {
                it.trim()
            }
            .filter {
                it.isNotEmpty()
            }
            .distinct()
    }
}
