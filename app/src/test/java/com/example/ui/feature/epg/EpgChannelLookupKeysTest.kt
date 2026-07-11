package com.example.ui.feature.epg

import com.example.data.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgChannelLookupKeysTest {

    private fun createChannel(
        id: String = "channel1",
        epgId: String = "epg1"
    ): LiveChannel {
        return LiveChannel(
            id = id,
            name = "Channel 1",
            streamUrl = "http://test",
            logoUrl = "",
            categoryId = "cat1",
            categoryName = "Cat 1",
            epgId = epgId,
            channelNumber = 1,
            isLocked = false,
            isAdult = false
        )
    }

    @Test
    fun testValidEpgIdGeneratesEpgIdAndChannelId() {
        val channel = createChannel(id = "ch1", epgId = "epg_xyz")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("epg_xyz", "ch1"), keys)
    }

    @Test
    fun testMissingEpgIdGeneratesChannelIdOnly() {
        val channel = createChannel(id = "ch1", epgId = "")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("ch1"), keys)
    }

    @Test
    fun testSameEpgIdAndChannelIdGeneratesOneKey() {
        val channel = createChannel(id = "ch1", epgId = "ch1")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("ch1"), keys)
    }
}
