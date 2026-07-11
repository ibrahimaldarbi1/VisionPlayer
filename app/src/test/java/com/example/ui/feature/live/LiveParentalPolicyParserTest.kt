package com.example.ui.feature.live

import org.junit.Assert.*
import org.junit.Test

class LiveParentalPolicyParserTest {

    @Test
    fun testParse_emptyOrNullValues() {
        val status = LiveParentalPolicyParser.parse(null, null)
        assertFalse(status.pinConfigured)
        assertFalse(status.hideAdultContent)
        assertTrue(status.lockedCategoryIds.isEmpty())

        val status2 = LiveParentalPolicyParser.parse("", "")
        assertFalse(status2.pinConfigured)
        assertFalse(status2.hideAdultContent)
        assertTrue(status2.lockedCategoryIds.isEmpty())
    }

    @Test
    fun testParse_pinConfiguration() {
        val status = LiveParentalPolicyParser.parse("1234", "")
        assertTrue(status.pinConfigured)

        val statusWithBlank = LiveParentalPolicyParser.parse("   ", "")
        assertFalse(statusWithBlank.pinConfigured)
    }

    @Test
    fun testParse_extractsLockedCategoryIds() {
        val status = LiveParentalPolicyParser.parse("1234", "cat_1,cat_2, cat_3 ")
        assertTrue(status.pinConfigured)
        assertFalse(status.hideAdultContent)
        assertEquals(setOf("cat_1", "cat_2", "cat_3"), status.lockedCategoryIds)
    }

    @Test
    fun testParse_adultContentMarker() {
        val status = LiveParentalPolicyParser.parse("1234", "cat_1,${HIDE_ADULT_CONTENT_MARKER},cat_2")
        assertTrue(status.hideAdultContent)
        assertEquals(setOf("cat_1", "cat_2"), status.lockedCategoryIds)
    }

    @Test
    fun testParse_ignoresDuplicateAndEmptyTokens() {
        val status = LiveParentalPolicyParser.parse("1234", "cat_1,,cat_1, ,cat_2")
        assertEquals(setOf("cat_1", "cat_2"), status.lockedCategoryIds)
    }
}
