package com.example.ui.feature.live

import com.example.data.LiveChannel
import org.junit.Assert.*
import org.junit.Test

class LiveParentalPresentationPolicyTest {

    @Test
    fun categoryShowsLock_lockedCategory_returnsTrue() {
        val result = LiveParentalPresentationPolicy.categoryShowsLock(
            parentalControlsEnabled = true,
            categoryId = "cat_1",
            lockedCategoryIds = setOf("cat_1", "cat_2")
        )
        assertTrue(result)
    }

    @Test
    fun categoryShowsLock_unlockedCategory_returnsFalse() {
        val result = LiveParentalPresentationPolicy.categoryShowsLock(
            parentalControlsEnabled = true,
            categoryId = "cat_3",
            lockedCategoryIds = setOf("cat_1", "cat_2")
        )
        assertFalse(result)
    }

    @Test
    fun categoryShowsLock_disabledParentalFeature_returnsFalse() {
        val result = LiveParentalPresentationPolicy.categoryShowsLock(
            parentalControlsEnabled = false,
            categoryId = "cat_1",
            lockedCategoryIds = setOf("cat_1", "cat_2")
        )
        assertFalse(result)
    }

    @Test
    fun channelShowsLock_adultChannel_returnsTrue() {
        val channel = createMockChannel(isAdult = true)
        val result = LiveParentalPresentationPolicy.channelShowsLock(
            parentalControlsEnabled = true,
            channel = channel,
            lockedCategoryIds = setOf("cat_1")
        )
        assertTrue(result)
    }

    @Test
    fun channelShowsLock_explicitlyLockedChannel_returnsTrue() {
        val channel = createMockChannel(isLocked = true)
        val result = LiveParentalPresentationPolicy.channelShowsLock(
            parentalControlsEnabled = true,
            channel = channel,
            lockedCategoryIds = setOf("cat_1")
        )
        assertTrue(result)
    }

    @Test
    fun channelShowsLock_channelInLockedCategory_returnsTrue() {
        val channel = createMockChannel(categoryId = "cat_1")
        val result = LiveParentalPresentationPolicy.channelShowsLock(
            parentalControlsEnabled = true,
            channel = channel,
            lockedCategoryIds = setOf("cat_1")
        )
        assertTrue(result)
    }

    @Test
    fun channelShowsLock_ordinaryChannel_returnsFalse() {
        val channel = createMockChannel(categoryId = "cat_2")
        val result = LiveParentalPresentationPolicy.channelShowsLock(
            parentalControlsEnabled = true,
            channel = channel,
            lockedCategoryIds = setOf("cat_1")
        )
        assertFalse(result)
    }

    @Test
    fun channelShowsLock_disabledParentalFeature_returnsFalse() {
        val channel = createMockChannel(isAdult = true, isLocked = true, categoryId = "cat_1")
        val result = LiveParentalPresentationPolicy.channelShowsLock(
            parentalControlsEnabled = false,
            channel = channel,
            lockedCategoryIds = setOf("cat_1")
        )
        assertFalse(result)
    }

    private fun createMockChannel(
        isAdult: Boolean = false,
        isLocked: Boolean = false,
        categoryId: String = "default_cat"
    ): LiveChannel {
        return LiveChannel(
            id = "ch_1",
            name = "Channel 1",
            streamUrl = "http://example.com/stream",
            logoUrl = "",
            categoryId = categoryId,
            categoryName = "Default Cat",
            epgId = "",
            channelNumber = 1,
            isLocked = isLocked,
            isAdult = isAdult
        )
    }
}
