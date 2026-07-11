package com.example.ui.feature.epg

import com.example.data.EpgProgramEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryEpgDataSourceTest {
    class FakeEpgRepositoryGateway : EpgRepositoryGateway {
        val observedChannelIds = mutableListOf<String>()
        var flowsToReturn = mutableMapOf<String, Flow<List<EpgProgramEntity>>>()
        
        override fun observePrograms(channelId: String): Flow<List<EpgProgramEntity>> {
            observedChannelIds.add(channelId)
            return flowsToReturn[channelId] ?: flowOf(emptyList())
        }
    }

    @Test
    fun testEmptyLookupKeyListEmitsEmptyList() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        val result = dataSource.observePrograms("provider1", emptyList()).first()
        assertEquals(emptyList<EpgProgramEntity>(), result)
    }

    @Test
    fun testBlankLookupKeysAreIgnored() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        val expected = listOf(EpgProgramEntity("id", "Title", "Desc", 0L, 1L, "epg1"))
        mockGateway.flowsToReturn["epg1"] = flowOf(expected)
        val result = dataSource.observePrograms("provider1", listOf("  ", "epg1", "\t")).first()
        assertEquals(expected, result)
        assertEquals(listOf("epg1"), mockGateway.observedChannelIds)
    }

    @Test
    fun testDuplicateLookupKeysAreRemoved() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        val result = dataSource.observePrograms("provider1", listOf("epg1", "epg1")).first()
        assertEquals(listOf("epg1"), mockGateway.observedChannelIds)
    }

    @Test
    fun testOneKeyObservesExactlyOneGatewayFlow() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        dataSource.observePrograms("provider1", listOf("epg1")).first()
        assertEquals(listOf("epg1"), mockGateway.observedChannelIds)
    }

    @Test
    fun testTwoKeysObserveBothGatewayFlows() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        dataSource.observePrograms("provider1", listOf("epg1", "epg2")).first()
        assertEquals(listOf("epg1", "epg2"), mockGateway.observedChannelIds)
    }

    @Test
    fun testResultsFromMultipleKeysAreMerged() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        val prog1 = EpgProgramEntity("id1", "Title1", "Desc", 0L, 1L, "epg1")
        val prog2 = EpgProgramEntity("id2", "Title2", "Desc", 2L, 3L, "epg2")
        mockGateway.flowsToReturn["epg1"] = flowOf(listOf(prog1))
        mockGateway.flowsToReturn["epg2"] = flowOf(listOf(prog2))
        val result = dataSource.observePrograms("provider1", listOf("epg1", "epg2")).first()
        assertEquals(listOf(prog1, prog2), result)
    }

    @Test
    fun testDuplicateProgramsAreRemoved() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        val prog1 = EpgProgramEntity("id1", "Title", "Desc", 0L, 1L, "epg1")
        val prog2 = EpgProgramEntity("id1", "Title", "Desc", 0L, 1L, "epg1")
        mockGateway.flowsToReturn["epg1"] = flowOf(listOf(prog1))
        mockGateway.flowsToReturn["epg2"] = flowOf(listOf(prog2))
        val result = dataSource.observePrograms("provider1", listOf("epg1", "epg2")).first()
        assertEquals(listOf(prog1), result)
    }

    @Test
    fun testProgramsAreOrderedByStartTime() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        val prog1 = EpgProgramEntity("id1", "Title", "Desc", 100L, 200L, "epg1")
        val prog2 = EpgProgramEntity("id2", "Title", "Desc", 50L, 100L, "epg1")
        mockGateway.flowsToReturn["epg1"] = flowOf(listOf(prog1, prog2))
        val result = dataSource.observePrograms("provider1", listOf("epg1")).first()
        assertEquals(listOf(prog2, prog1), result)
    }

    @Test
    fun testEqualStartTimesAreOrderedByEndTime() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        val prog1 = EpgProgramEntity("id1", "Title", "Desc", 100L, 300L, "epg1")
        val prog2 = EpgProgramEntity("id2", "Title", "Desc", 100L, 200L, "epg1")
        mockGateway.flowsToReturn["epg1"] = flowOf(listOf(prog1, prog2))
        val result = dataSource.observePrograms("provider1", listOf("epg1")).first()
        assertEquals(listOf(prog2, prog1), result)
    }

    @Test
    fun testEquivalentMergedEmissionsAreSuppressed() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        val prog1 = EpgProgramEntity("id1", "Title", "Desc", 100L, 200L, "epg1")
        mockGateway.flowsToReturn["epg1"] = flow {
            emit(listOf(prog1))
            emit(listOf(prog1)) // Equivalent
            emit(emptyList()) // Different
        }
        val results = mutableListOf<List<EpgProgramEntity>>()
        val job = launch {
            dataSource.observePrograms("provider1", listOf("epg1")).toList(results)
        }
        advanceUntilIdle()
        job.cancel()
        assertEquals(2, results.size)
        assertEquals(listOf(prog1), results[0])
        assertEquals(emptyList<EpgProgramEntity>(), results[1])
    }

    @Test
    fun testGatewayFailurePropagatesDownstream() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        mockGateway.flowsToReturn["epg1"] = flow {
            throw RuntimeException("Gateway failure")
        }
        var exception: Throwable? = null
        try {
            dataSource.observePrograms("provider1", listOf("epg1")).first()
        } catch (e: Exception) {
            exception = e
        }
        assertEquals("Gateway failure", exception?.message)
    }

    @Test
    fun testProviderIdDoesNotModifyLookupKeyText() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        dataSource.observePrograms("provider1", listOf("epg1")).first()
        assertEquals(listOf("epg1"), mockGateway.observedChannelIds)
    }

    @Test
    fun testKeyOrderIsPreserved() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        dataSource.observePrograms("provider1", listOf("keyA", "keyB", "keyC")).first()
        assertEquals(listOf("keyA", "keyB", "keyC"), mockGateway.observedChannelIds)
    }

    @Test
    fun testNoRoomOrNetworkOrRealRepositoryIsUsed() = runTest {
        val mockGateway = FakeEpgRepositoryGateway()
        val dataSource = RepositoryEpgDataSource(mockGateway)
        dataSource.observePrograms("provider1", listOf("epg1")).first()
        // Merely instantiated the fake and RepositoryEpgDataSource. 
        // We assert true as we didn't use real resources.
        assertEquals(listOf("epg1"), mockGateway.observedChannelIds)
    }
}
