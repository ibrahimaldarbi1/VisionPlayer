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
    fun testStrongMatchWithAliases() {
        // Test that "Man City vs PSG" matches "Manchester City" and "Paris Saint-Germain"
        val match = FootballMatch(
            matchId = 102,
            competitionCode = "CL",
            competitionName = "Champions League",
            competitionEmblemUrl = "crest.png",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            status = "SCHEDULED",
            matchday = 1,
            stage = "Regular Season",
            homeTeamId = 5,
            homeTeamName = "Manchester City",
            homeTeamCrestUrl = "mancity.png",
            awayTeamId = 6,
            awayTeamName = "Paris Saint-Germain",
            awayTeamCrestUrl = "psg.png",
            homeScore = null,
            awayScore = null
        )

        val program = EpgProgramEntity(
            channelId = "ch1",
            title = "Man City v PSG Live",
            description = "UEFA Champions League Clash",
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
    fun testChannelMatchingWithEpgId() {
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

        // EpgProgramEntity's channelId represents the XMLTV channel ID,
        // which corresponds to LiveChannel's epgId (e.g. "sports_epg") and NOT LiveChannel's stream ID (e.g. "ch1").
        val program = EpgProgramEntity(
            channelId = "sports_epg",
            title = "Real Madrid vs Barcelona Live",
            description = "El Clasico",
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
    }

    @Test
    fun testXmltvDateParser() {
        val dateUtc = XmltvEpgParser.parseXmltvDate("20260709190000 +0000")
        assertNotNull(dateUtc)

        val dateWithOffset = XmltvEpgParser.parseXmltvDate("20260709190000 +0300")
        assertNotNull(dateWithOffset)

        // The difference must be exactly 3 hours (3 * 3600 * 1000 milliseconds)
        val diff = dateUtc!! - dateWithOffset!!
        assertEquals(3 * 3600 * 1000L, diff)

        val dateInvalid = XmltvEpgParser.parseXmltvDate("invalid_date_format")
        assertNull(dateInvalid)
    }

    @Test
    fun testXmltvParsing() {
        val xmlData = """
            <tv>
              <channel id="bein_sports_1">
                <display-name>beIN Sports 1</display-name>
              </channel>
              <programme channel="bein_sports_1" start="20260709190000 +0000" stop="20260709210000 +0000">
                <title>Real Madrid vs Barcelona</title>
                <desc>La Liga El Clasico</desc>
              </programme>
            </tv>
        """.trimIndent()

        val programs = XmltvEpgParser.parseXmltv(xmlData)
        assertEquals(1, programs.size)

        val program = programs[0]
        assertEquals("bein_sports_1", program.channelId)
        assertEquals("Real Madrid vs Barcelona", program.title)
        assertEquals("La Liga El Clasico", program.description)
        assertNotNull(program.startTime)
        assertNotNull(program.endTime)
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
