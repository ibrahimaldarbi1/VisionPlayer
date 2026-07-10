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

    fun isSportsChannelName(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val normalized = normalizeChannelName(value)
        val patterns = listOf(
            "sport", "bein", "sky", "espn", "ssc", "kass", "dubai", "abu dhabi", "tnt", "arena", "euro", "canal", "eleven", "liga", "premier", "سبورت", "رياض"
        )
        return patterns.any { pattern -> normalized.contains(pattern) }
    }

    fun isSportsChannel(channel: LiveChannel): Boolean {
        return isSportsChannelName(channel.name) ||
               isSportsChannelName(channel.epgId) ||
               isSportsChannelName(channel.id) ||
               channel.categoryName?.lowercase()?.contains("sport") == true
    }

    fun extractBeinChannelNumber(value: String?): String? {
        if (value == null) return null
        val normalized = normalizeChannelName(value)
        
        // Protect against treating 4K / 4 k as channel number 4
        if (normalized.contains("4k") || normalized.contains("4 k")) {
            return null
        }
        
        // Target sports, max, xtra, fr, en, ar followed by spaces and a number
        val regex = Regex("\\b(sports|max|xtra|fr|en|ar)\\s+(\\d+)\\b")
        val match = regex.find(normalized)
        if (match != null) {
            return match.groupValues[2]
        }
        
        // Also support "bein <number>"
        val regexBein = Regex("\\bbein\\s+(\\d+)\\b")
        val matchBein = regexBein.find(normalized)
        if (matchBein != null) {
            return matchBein.groupValues[1]
        }
        
        return null
    }

    fun isBeinMaxChannel(value: String?): Boolean {
        if (value == null) return false
        val normalized = normalizeChannelName(value)
        return normalized.contains("max")
    }

    fun isBein4kChannel(value: String?): Boolean {
        if (value == null) return false
        val normalized = normalizeChannelName(value)
        return normalized.contains("4k")
    }

    fun findLocalBeinChannelForGuideEvent(
        eventChannelName: String?,
        eventChannelNumber: String?,
        localChannels: List<LiveChannel>
    ): LiveChannel? {
        if (!isBeinChannelName(eventChannelName)) return null

        val beinLocalChannels =
            if (
                localChannels.all {
                    isBeinChannel(it)
                }
            ) {
                localChannels
            } else {
                localChannels.filter {
                    isBeinChannel(it)
                }
            }
        if (beinLocalChannels.isEmpty()) return null

        val eventNorm = normalizeChannelName(eventChannelName)
        val eventNum = eventChannelNumber ?: extractBeinChannelNumber(eventChannelName)
        val eventIsMax = isBeinMaxChannel(eventChannelName)
        val eventIs4k = isBein4kChannel(eventChannelName)

        // Priority 1: Exact normalized channel name match
        val normNameMatch = beinLocalChannels.find { ch ->
            normalizeChannelName(ch.name) == eventNorm ||
            normalizeChannelName(ch.epgId) == eventNorm ||
            normalizeChannelName(ch.id) == eventNorm
        }
        if (normNameMatch != null) return normNameMatch

        // Priority 2: Exact beIN channel number match
        if (!eventNum.isNullOrBlank()) {
            val numberMatch = beinLocalChannels.find { ch ->
                val chNum = extractBeinChannelNumber(ch.name) ?: extractBeinChannelNumber(ch.epgId) ?: extractBeinChannelNumber(ch.id)
                val chIsMax = isBeinMaxChannel(ch.name) || isBeinMaxChannel(ch.epgId) || isBeinMaxChannel(ch.id)
                val chIs4k = isBein4kChannel(ch.name) || isBein4kChannel(ch.epgId) || isBein4kChannel(ch.id)
                
                chNum == eventNum && chIsMax == eventIsMax && chIs4k == eventIs4k
            }
            if (numberMatch != null) return numberMatch
        }

        // Priority 3: Max channel match
        if (eventIsMax) {
            val maxMatch = beinLocalChannels.find { ch ->
                val chIsMax = isBeinMaxChannel(ch.name) || isBeinMaxChannel(ch.epgId) || isBeinMaxChannel(ch.id)
                chIsMax && (eventNum == null || (extractBeinChannelNumber(ch.name) == eventNum))
            }
            if (maxMatch != null) return maxMatch
        }

        // Priority 4: 4K channel match
        if (eventIs4k) {
            val match4k = beinLocalChannels.find { ch ->
                isBein4kChannel(ch.name) || isBein4kChannel(ch.epgId) || isBein4kChannel(ch.id)
            }
            if (match4k != null) return match4k
        }

        // Priority 5: Safe contains match
        val containsMatch = beinLocalChannels.find { ch ->
            val chNorm = normalizeChannelName(ch.name)
            val chIsMax = isBeinMaxChannel(ch.name) || isBeinMaxChannel(ch.epgId) || isBeinMaxChannel(ch.id)
            val chIs4k = isBein4kChannel(ch.name) || isBein4kChannel(ch.epgId) || isBein4kChannel(ch.id)
            val chNum = extractBeinChannelNumber(ch.name) ?: extractBeinChannelNumber(ch.epgId) ?: extractBeinChannelNumber(ch.id)
            
            val matchesCategory = (chIsMax == eventIsMax) && (chIs4k == eventIs4k)
            val matchesNumber = (chNum == eventNum)
            
            matchesCategory && matchesNumber && (chNorm.contains(eventNorm) || eventNorm.contains(chNorm))
        }
        if (containsMatch != null) return containsMatch

        return null
    }
}
