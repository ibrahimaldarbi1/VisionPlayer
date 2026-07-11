package com.example.ui.feature.epg

import com.example.data.EpgProgramEntity
import com.example.data.IptvRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal interface EpgRepositoryGateway {

    fun observePrograms(
        channelId: String
    ): Flow<List<EpgProgramEntity>>
}

private class IptvEpgRepositoryGateway(
    private val repository: IptvRepository
) : EpgRepositoryGateway {

    override fun observePrograms(
        channelId: String
    ): Flow<List<EpgProgramEntity>> {
        return repository.getEpgForChannel(channelId)
    }
}

class RepositoryEpgDataSource internal constructor(
    private val gateway: EpgRepositoryGateway
) : EpgDataSource {

    constructor(
        repository: IptvRepository
    ) : this(
        gateway = IptvEpgRepositoryGateway(repository)
    )

    override fun observePrograms(
        providerId: String,
        channelLookupIds: List<String>
    ): Flow<List<EpgProgramEntity>> {
        val normalizedIds = channelLookupIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (normalizedIds.isEmpty()) {
            return flowOf(emptyList())
        }

        val programFlows = normalizedIds.map { channelId ->
            gateway.observePrograms(channelId)
        }

        val merged = if (programFlows.size == 1) {
            programFlows.first()
        } else {
            combine(programFlows) { emissions ->
                emissions.flatMap { it }
            }
        }

        return merged
            .map { programs ->
                programs
                    .distinctBy { program ->
                        listOf(
                            program.channelId,
                            program.startTime,
                            program.endTime,
                            program.title
                        )
                    }
                    .sortedWith(
                        compareBy<EpgProgramEntity> { it.startTime }
                            .thenBy { it.endTime }
                    )
            }
            .distinctUntilChanged()
    }
}
