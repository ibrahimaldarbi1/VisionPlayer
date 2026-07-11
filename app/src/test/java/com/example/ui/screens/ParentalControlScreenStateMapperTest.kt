package com.example.ui.screens

import com.example.ui.feature.live.LiveParentalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalControlScreenStateMapperTest {

    @Test
    fun nullStatus_producesIsPinSetFalse() {
        val result = ParentalControlScreenStateMapper.fromStatus(null)
        assertFalse(result.isPinSet)
    }

    @Test
    fun nullStatus_producesIsUnlockedTrue() {
        val result = ParentalControlScreenStateMapper.fromStatus(null)
        assertTrue(result.isUnlocked)
    }

    @Test
    fun nullStatus_clearsHidingAndLockedIds() {
        val result = ParentalControlScreenStateMapper.fromStatus(null)
        assertFalse(result.hideAdultContent)
        assertTrue(result.lockedCategoryIds.isEmpty())
    }

    @Test
    fun configuredPin_producesIsPinSetTrue() {
        val status = LiveParentalStatus(
            pinConfigured = true,
            hideAdultContent = false,
            lockedCategoryIds = emptySet()
        )
        val result = ParentalControlScreenStateMapper.fromStatus(status)
        assertTrue(result.isPinSet)
    }

    @Test
    fun configuredPin_producesIsUnlockedFalse() {
        val status = LiveParentalStatus(
            pinConfigured = true,
            hideAdultContent = false,
            lockedCategoryIds = emptySet()
        )
        val result = ParentalControlScreenStateMapper.fromStatus(status)
        assertFalse(result.isUnlocked)
    }

    @Test
    fun configuredPin_preservesLockedCategoryIds() {
        val expectedIds = setOf("cat_1", "cat_2")
        val status = LiveParentalStatus(
            pinConfigured = true,
            hideAdultContent = false,
            lockedCategoryIds = expectedIds
        )
        val result = ParentalControlScreenStateMapper.fromStatus(status)
        assertEquals(expectedIds, result.lockedCategoryIds)
    }

    @Test
    fun configuredPin_preservesHideMode() {
        val status = LiveParentalStatus(
            pinConfigured = true,
            hideAdultContent = true,
            lockedCategoryIds = emptySet()
        )
        val result = ParentalControlScreenStateMapper.fromStatus(status)
        assertTrue(result.hideAdultContent)
    }

    @Test
    fun noConfiguredPin_producesIsPinSetFalse() {
        val status = LiveParentalStatus(
            pinConfigured = false,
            hideAdultContent = true,
            lockedCategoryIds = setOf("cat_3")
        )
        val result = ParentalControlScreenStateMapper.fromStatus(status)
        assertFalse(result.isPinSet)
    }

    @Test
    fun noConfiguredPin_producesIsUnlockedTrue() {
        val status = LiveParentalStatus(
            pinConfigured = false,
            hideAdultContent = true,
            lockedCategoryIds = setOf("cat_3")
        )
        val result = ParentalControlScreenStateMapper.fromStatus(status)
        assertTrue(result.isUnlocked)
    }

    @Test
    fun mappingProviderAUnlockedThenProviderBConfiguredPin_resultsInProviderBLocked() {
        val providerAStatus = LiveParentalStatus(
            pinConfigured = false,
            hideAdultContent = false,
            lockedCategoryIds = emptySet()
        )
        val stateA = ParentalControlScreenStateMapper.fromStatus(providerAStatus)
        assertTrue(stateA.isUnlocked)

        val providerBStatus = LiveParentalStatus(
            pinConfigured = true,
            hideAdultContent = false,
            lockedCategoryIds = emptySet()
        )
        val stateB = ParentalControlScreenStateMapper.fromStatus(providerBStatus)
        assertFalse(stateB.isUnlocked)
    }

    @Test
    fun mappingPopulatedPolicyThenNullStatus_producesEmptyPolicy() {
        val populatedStatus = LiveParentalStatus(
            pinConfigured = true,
            hideAdultContent = true,
            lockedCategoryIds = setOf("cat_1")
        )
        val state1 = ParentalControlScreenStateMapper.fromStatus(populatedStatus)
        assertTrue(state1.hideAdultContent)
        assertEquals(1, state1.lockedCategoryIds.size)

        val state2 = ParentalControlScreenStateMapper.fromStatus(null)
        assertFalse(state2.hideAdultContent)
        assertTrue(state2.lockedCategoryIds.isEmpty())
    }

    @Test
    fun mappedState_doesNotContainOrExposePinField() {
        val properties = ParentalControlScreenLoadState::class.java.declaredFields.map { it.name }
        assertFalse("State should not contain PIN property", properties.contains("pin"))
        assertFalse("State should not contain PIN property", properties.contains("storedPin"))
    }
}
