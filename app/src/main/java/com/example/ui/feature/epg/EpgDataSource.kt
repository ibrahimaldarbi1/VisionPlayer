package com.example.ui.feature.epg

import com.example.data.EpgProgramEntity
import kotlinx.coroutines.flow.Flow

interface EpgDataSource {

    fun observePrograms(
        providerId: String,
        channelLookupIds: List<String>
    ): Flow<List<EpgProgramEntity>>
}
