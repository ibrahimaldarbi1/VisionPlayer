package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FootballFeatureTest {

    @Test
    fun testStringNormalization() {
        val original = "Real Madrid CF & Barcelona FC!"
        val expected = "real madrid and barcelona"
        assertEquals(expected, FootballMatchUtils.normalize(original))
    }

    @Test
    fun testStrongMatch() {
        // EPG title/description contains both home and away teams
        val match = FootballMatch(
            matchId = 101,
            competitionCode = "LL",
            competitionName = "La Liga",
            competitionEmblemUrl = "crest.png",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            status = "SCHEDULED",
            matchday = 1,
            stage = "Regular Season",
            homeTeamId = 1,
            homeTeamName = "Real Madrid",
            homeTeamCrestUrl = "rm.png",
            awayTeamId = 2,
            awayTeamName = "Barcelona",
            awayTeamCrestUrl = "barca.png",
            homeScore = null,
            awayScore = null
        )
        
        val program = EpgProgramEntity(
            channelId = "ch1",
            title = "Real Madrid vs Barcelona Live",
            description = "El Clasico derby live match",
            startTime = 0L,
            endTime = 1000L
        )

        val channel = LiveChannel(
            id = "ch1",
            name = "Sports HD",
            streamUrl = "http://test",
            logoUrl = "logo.png",
            categoryId = "sports",
            categoryName = "Sports",
            epgId = "sports_epg",
            channelNumber = 1
        )

        val result = FootballMatchUtils.matchMatchWithEpg(match, listOf(program), listOf(channel))
        assertEquals("STRONG", result.confidence)
        assertEquals("Sports HD", result.matchedChannel?.name)
        assertEquals("Real Madrid vs Barcelona Live", result.matchedProgramName)
    }

    @Test
    fun testMediumMatch() {
        // EPG contains one team and competition name
        val match = FootballMatch(
            matchId = 101,
            competitionCode = "LL",
            competitionName = "La Liga",
            competitionEmblemUrl = "crest.png",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            status = "SCHEDULED",
            matchday = 1,
            stage = "Regular Season",
            homeTeamId = 1,
            homeTeamName = "Real Madrid",
            homeTeamCrestUrl = "rm.png",
            awayTeamId = 2,
            awayTeamName = "Barcelona",
            awayTeamCrestUrl = "barca.png",
            homeScore = null,
            awayScore = null
        )

        val program = EpgProgramEntity(
            channelId = "ch1",
            title = "Real Madrid Match Live",
            description = "Action from La Liga",
            startTime = 0L,
            endTime = 1000L
        )

        val channel = LiveChannel(
            id = "ch1",
            name = "Sports HD",
            streamUrl = "http://test",
            logoUrl = "logo.png",
            categoryId = "sports",
            categoryName = "Sports",
            epgId = "sports_epg",
            channelNumber = 1
        )

        val result = FootballMatchUtils.matchMatchWithEpg(match, listOf(program), listOf(channel))
        assertEquals("MEDIUM", result.confidence)
        assertEquals("Sports HD", result.matchedChannel?.name)
    }

    @Test
    fun testNoMatch() {
        val match = FootballMatch(
            matchId = 101,
            competitionCode = "LL",
            competitionName = "La Liga",
            competitionEmblemUrl = "crest.png",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            status = "SCHEDULED",
            matchday = 1,
            stage = "Regular Season",
            homeTeamId = 1,
            homeTeamName = "Real Madrid",
            homeTeamCrestUrl = "rm.png",
            awayTeamId = 2,
            awayTeamName = "Barcelona",
            awayTeamCrestUrl = "barca.png",
            homeScore = null,
            awayScore = null
        )

        val program = EpgProgramEntity(
            channelId = "ch1",
            title = "Chelsea vs Arsenal",
            description = "Premier League matches",
            startTime = 0L,
            endTime = 1000L
        )

        val channel = LiveChannel(
            id = "ch1",
            name = "Sports HD",
            streamUrl = "http://test",
            logoUrl = "logo.png",
            categoryId = "sports",
            categoryName = "Sports",
            epgId = "sports_epg",
            channelNumber = 1
        )

        val result = FootballMatchUtils.matchMatchWithEpg(match, listOf(program), listOf(channel))
        assertEquals("NONE", result.confidence)
        assertNull(result.matchedChannel)
    }

    @Test
    fun testPrefsStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FootballPrefs(context)

        assertFalse(prefs.hasSeenFootballSetup)
        prefs.hasSeenFootballSetup = true
        assertTrue(prefs.hasSeenFootballSetup)

        assertTrue(prefs.selectedFootballCompetitionCodes.isEmpty())
        prefs.selectedFootballCompetitionCodes = listOf("PL", "CL")
        assertEquals(listOf("PL", "CL"), prefs.selectedFootballCompetitionCodes)

        assertTrue(prefs.selectedFootballTeamIds.isEmpty())
        prefs.selectedFootballTeamIds = listOf(81, 86)
        assertEquals(listOf(81, 86), prefs.selectedFootballTeamIds)
    }
}
