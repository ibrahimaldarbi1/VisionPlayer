package com.example.ui.feature.epg

import com.example.data.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgChannelLookupKeysTest {
    private fun createChannel(
        id: String = "channel1",
        epgId: String = "epg1",
        name: String = "Channel 1",
        categoryName: String = "Cat 1",
        streamUrl: String = "http://test"
    ): LiveChannel {
        return LiveChannel(
            id = id,
            name = name,
            streamUrl = streamUrl,
            logoUrl = "",
            categoryId = "cat1",
            categoryName = categoryName,
            epgId = epgId,
            channelNumber = 1,
            isLocked = false,
            isAdult = false
        )
    }

    @Test
    fun testEpgIdIsFirst() {
        val channel = createChannel(id = "ch1", epgId = "epg_xyz")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals("epg_xyz", keys.first())
    }

    @Test
    fun testChannelIdIsFallback() {
        val channel = createChannel(id = "ch1", epgId = "")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("ch1"), keys)
    }

    @Test
    fun testBlankEpgIdIsRemoved() {
        val channel = createChannel(id = "ch1", epgId = "   ")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("ch1"), keys)
    }

    @Test
    fun testBlankChannelIdIsRemoved() {
        val channel = createChannel(id = "   ", epgId = "epg1")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("epg1"), keys)
    }

    @Test
    fun testEqualIdsAreDeduplicated() {
        val channel = createChannel(id = "shared_id", epgId = "shared_id")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("shared_id"), keys)
    }

    @Test
    fun testLeadingAndTrailingWhitespaceIsTrimmed() {
        val channel = createChannel(id = " ch1 ", epgId = "\tepg1\n")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("epg1", "ch1"), keys)
    }

    @Test
    fun testKeyCasingIsPreserved() {
        val channel = createChannel(id = "ChAnNeL1", epgId = "EpgId")
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("EpgId", "ChAnNeL1"), keys)
    }

    @Test
    fun testNamesAndUrlsAreNeverUsedAsLookupKeys() {
        val channel = createChannel(
            id = "ch1", 
            epgId = "epg1",
            name = "My Channel",
            categoryName = "My Category",
            streamUrl = "http://my.stream"
        )
        val keys = EpgChannelLookupKeys.forChannel(channel)
        assertEquals(listOf("epg1", "ch1"), keys)
    }
}
