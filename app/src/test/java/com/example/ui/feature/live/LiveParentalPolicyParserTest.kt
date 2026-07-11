package com.example.ui.feature.live

import org.junit.Assert.*
import org.junit.Test

class LiveParentalPolicyParserTest {
    @Test
    fun parse_nullPinAndPolicy_returnsDefaults() {
        val status = LiveParentalPolicyParser.parse(null, null)
        assertFalse(status.pinConfigured)
        assertFalse(status.hideAdultContent)
        assertTrue(status.lockedCategoryIds.isEmpty())
    }

    @Test
    fun parse_emptyPolicy_returnsEmptySet() {
        val status = LiveParentalPolicyParser.parse("1234", "")
        assertTrue(status.pinConfigured)
        assertFalse(status.hideAdultContent)
        assertTrue(status.lockedCategoryIds.isEmpty())
    }

    @Test
    fun parse_blankPin_isNotConfigured() {
        val status = LiveParentalPolicyParser.parse("   ", "cat_1")
        assertFalse(status.pinConfigured)
    }

    @Test
    fun parse_nonblankPin_isConfigured() {
        val status = LiveParentalPolicyParser.parse("0000", "")
        assertTrue(status.pinConfigured)
    }

    @Test
    fun parse_multipleCategoryIds_areExtracted() {
        val status = LiveParentalPolicyParser.parse("1234", "cat_1,cat_2")
        assertEquals(setOf("cat_1", "cat_2"), status.lockedCategoryIds)
    }

    @Test
    fun parse_whitespaceTokens_areTrimmed() {
        val status = LiveParentalPolicyParser.parse("1234", " cat_1 ,  cat_2  ")
        assertEquals(setOf("cat_1", "cat_2"), status.lockedCategoryIds)
    }

    @Test
    fun parse_blankTokens_areRemoved() {
        val status = LiveParentalPolicyParser.parse("1234", "cat_1,, ,cat_2")
        assertEquals(setOf("cat_1", "cat_2"), status.lockedCategoryIds)
    }

    @Test
    fun parse_duplicateIds_areRemoved() {
        val status = LiveParentalPolicyParser.parse("1234", "cat_1,cat_1,cat_2")
        assertEquals(setOf("cat_1", "cat_2"), status.lockedCategoryIds)
    }

    @Test
    fun parse_exactMarker_enablesHiding() {
        val status = LiveParentalPolicyParser.parse("1234", "__HIDDEN__")
        assertTrue(status.hideAdultContent)
    }

    @Test
    fun parse_marker_isExcludedFromCategoryIds() {
        val status = LiveParentalPolicyParser.parse("1234", "cat_1,__HIDDEN__,cat_2")
        assertTrue(status.hideAdultContent)
        assertEquals(setOf("cat_1", "cat_2"), status.lockedCategoryIds)
    }

    @Test
    fun parse_markerWithAffixes_doesNotEnableHiding() {
        val status = LiveParentalPolicyParser.parse("1234", "prefix__HIDDEN__suffix")
        assertFalse(status.hideAdultContent)
        assertEquals(setOf("prefix__HIDDEN__suffix"), status.lockedCategoryIds)
    }

    @Test
    fun parse_lowercaseMarker_doesNotEnableHiding() {
        val status = LiveParentalPolicyParser.parse("1234", "__hidden__")
        assertFalse(status.hideAdultContent)
        assertEquals(setOf("__hidden__"), status.lockedCategoryIds)
    }

    @Test
    fun parse_categoryIdCasing_isPreserved() {
        val status = LiveParentalPolicyParser.parse("1234", "CaT_1,cAt_2")
        assertEquals(setOf("CaT_1", "cAt_2"), status.lockedCategoryIds)
    }

    @Test
    fun parse_markerOnlyPolicy_enablesHidingWithNoCategories() {
        val status = LiveParentalPolicyParser.parse("1234", "__HIDDEN__")
        assertTrue(status.hideAdultContent)
        assertTrue(status.lockedCategoryIds.isEmpty())
    }
}
