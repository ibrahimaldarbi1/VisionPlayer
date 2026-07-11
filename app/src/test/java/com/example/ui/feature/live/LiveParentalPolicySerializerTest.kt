package com.example.ui.feature.live

import org.junit.Assert.*
import org.junit.Test

class LiveParentalPolicySerializerTest {
    @Test
    fun serialize_emptyIdsAndNoHiding_returnsEmpty() {
        val result = LiveParentalPolicySerializer.serialize(emptyList(), false)
        assertEquals("", result)
    }

    @Test
    fun serialize_emptyIdsAndHiding_returnsMarkerOnly() {
        val result = LiveParentalPolicySerializer.serialize(emptyList(), true)
        assertEquals(HIDE_ADULT_CONTENT_MARKER, result)
    }

    @Test
    fun serialize_idTrimming_removesWhitespace() {
        val result = LiveParentalPolicySerializer.serialize(listOf(" cat_1 ", "cat_2"), false)
        assertEquals("cat_1,cat_2", result)
    }

    @Test
    fun serialize_blankIdRemoval_removesBlanks() {
        val result = LiveParentalPolicySerializer.serialize(listOf("cat_1", "", " ", "cat_2"), false)
        assertEquals("cat_1,cat_2", result)
    }

    @Test
    fun serialize_duplicateRemoval_keepsUnique() {
        val result = LiveParentalPolicySerializer.serialize(listOf("cat_1", "cat_1", "cat_2"), false)
        assertEquals("cat_1,cat_2", result)
    }

    @Test
    fun serialize_deterministicSorting_sortsIds() {
        val result = LiveParentalPolicySerializer.serialize(listOf("cat_b", "cat_a"), false)
        assertEquals("cat_a,cat_b", result)
    }

    @Test
    fun serialize_existingMarkerRemoval_ignoresInputMarker() {
        val result = LiveParentalPolicySerializer.serialize(listOf("cat_1", HIDE_ADULT_CONTENT_MARKER), false)
        assertEquals("cat_1", result)
    }

    @Test
    fun serialize_exactlyOneMarker_whenHidingEnabled() {
        val result = LiveParentalPolicySerializer.serialize(listOf("cat_1", HIDE_ADULT_CONTENT_MARKER), true)
        assertEquals("cat_1,$HIDE_ADULT_CONTENT_MARKER", result)
    }

    @Test
    fun serialize_markerAppearsLast() {
        val result = LiveParentalPolicySerializer.serialize(listOf("z_cat", "a_cat"), true)
        assertEquals("a_cat,z_cat,$HIDE_ADULT_CONTENT_MARKER", result)
    }

    @Test
    fun serialize_categoryIdCasing_isPreserved() {
        val result = LiveParentalPolicySerializer.serialize(listOf("CaT_1", "cAt_2"), false)
        assertEquals("CaT_1,cAt_2", result) // C comes before c in ASCII, so it might be ordered C, c. Wait, "CaT_1" vs "cAt_2" -> C is 67, c is 99. C comes first.
    }

    @Test
    fun serialize_unknownIdPreservation_keepsAllValidIds() {
        val result = LiveParentalPolicySerializer.serialize(listOf("unknown_id"), false)
        assertEquals("unknown_id", result)
    }

    @Test
    fun serializeThenParse_normalizedRoundTrip() {
        val serialized = LiveParentalPolicySerializer.serialize(listOf("b_cat", "a_cat", HIDE_ADULT_CONTENT_MARKER), true)
        val status = LiveParentalPolicyParser.parse("1234", serialized)
        assertEquals(setOf("a_cat", "b_cat"), status.lockedCategoryIds)
        assertTrue(status.hideAdultContent)
    }

    @Test
    fun serialize_disablingHiding_removesOnlyMarker() {
        val result = LiveParentalPolicySerializer.serialize(listOf("cat_1"), false)
        assertEquals("cat_1", result)
    }

    @Test
    fun serialize_differentInputOrdering_producesSameOutput() {
        val result1 = LiveParentalPolicySerializer.serialize(listOf("cat_1", "cat_2"), true)
        val result2 = LiveParentalPolicySerializer.serialize(listOf("cat_2", "cat_1"), true)
        assertEquals(result1, result2)
    }
}
