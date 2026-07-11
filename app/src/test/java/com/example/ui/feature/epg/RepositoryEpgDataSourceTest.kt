package com.example.ui.feature.epg

import com.example.data.EpgProgramEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryEpgDataSourceTest {

    class FakeEpgRepositoryGateway : EpgRepositoryGateway {
        var observedChannelId: String? = null
        var mockPrograms: List<EpgProgramEntity> = emptyList()

        override fun observePrograms(channelId: String): Flow<List<EpgProgramEntity>> {
            observedChannelId = channelId
            return flowOf(mockPrograms)
        }
    }

    @Test
    fun testRepositoryEpgDataSourceDelegatesCorrectly() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        
        val expectedPrograms = listOf(
            EpgProgramEntity("epg1", "Title", "Desc", 0L, 1L, "epg1")
        )
        mockGateway.mockPrograms = expectedPrograms
            
        val result = dataSource.observePrograms("provider1", listOf("epg1")).first()
        
        assertEquals(expectedPrograms, result)
        assertEquals("epg1", mockGateway.observedChannelId)
    }
}
