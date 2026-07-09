package com.example.data

import java.text.Normalizer

object FootballChannelMatcher {

    private fun normalizeArabic(text: String): String {
        var clean = text
        // Remove Arabic diacritics (harakat)
        clean = clean.replace(Regex("[\\u064B-\\u0652]"), "")
        // Normalize Alefs (أإآ -> ا)
        clean = clean.replace(Regex("[أإآ]"), "ا")
        // Normalize Teh Marbuta (ة -> ه)
        clean = clean.replace(Regex("ة"), "ه")
        // Normalize Yeh (ى -> ي)
        clean = clean.replace(Regex("ى"), "ي")
        return clean
    }

    fun normalizeChannelName(text: String?): String {
        if (text.isNullOrBlank()) return ""
        // Lowercase
        var clean = text.lowercase()
        // Replace & with and
        clean = clean.replace("&", "and")
        // Normalize Arabic letters
        clean = normalizeArabic(clean)
        // Strip accents
        val temp = Normalizer.normalize(clean, Normalizer.Form.NFD)
        clean = temp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        // Keep only letters (including Unicode letters like Arabic), digits, and spaces
        clean = clean.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
        // Collapse spaces
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean
    }

    fun isBeinChannelName(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val normalized = normalizeChannelName(value)
        
        // Define our match patterns
        val patterns = listOf(
            "bein",
            "be in",
            "بي ان",
            "بين سبورت",
            "بيان سبورت"
        )
        
        return patterns.any { pattern -> normalized.contains(pattern) }
    }

    fun isBeinChannel(channel: LiveChannel): Boolean {
        return isBeinChannelName(channel.name) ||
               isBeinChannelName(channel.epgId) ||
               isBeinChannelName(channel.id)
    }
}
