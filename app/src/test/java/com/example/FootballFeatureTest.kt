package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.firstOrNull

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

    @Test
    fun testBeInChannelDetection() {
        // beIN Sports 1 HD is detected as beIN.
        val channel1 = LiveChannel("ch1", "beIN Sports 1 HD", "url", "logo", "cat", "CatName", "epg1", 1)
        assertTrue(FootballChannelMatcher.isBeinChannel(channel1))

        // BEIN SPORTS Premium 1 is detected as beIN.
        val channel2 = LiveChannel("ch2", "BEIN SPORTS Premium 1", "url", "logo", "cat", "CatName", "epg2", 2)
        assertTrue(FootballChannelMatcher.isBeinChannel(channel2))

        // Arabic beIN channel name is detected as beIN.
        val channel3 = LiveChannel("ch3", "بي ان سبورت 1", "url", "logo", "cat", "CatName", "epg3", 3)
        assertTrue(FootballChannelMatcher.isBeinChannel(channel3))

        // Sky Sports is not detected as beIN.
        val channel4 = LiveChannel("ch4", "Sky Sports Main Event HD", "url", "logo", "cat", "CatName", "epg4", 4)
        assertFalse(FootballChannelMatcher.isBeinChannel(channel4))

        // ESPN is not detected as beIN.
        val channel5 = LiveChannel("ch5", "ESPN US HD", "url", "logo", "cat", "CatName", "epg5", 5)
        assertFalse(FootballChannelMatcher.isBeinChannel(channel5))
    }

    @Test
    fun testFootballMatchingSpecificToBeIn() {
        val match = FootballMatch(
            matchId = 201,
            competitionCode = "PL",
            competitionName = "Premier League",
            competitionEmblemUrl = "pl.png",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            status = "SCHEDULED",
            matchday = 1,
            stage = "Regular",
            homeTeamId = 1,
            homeTeamName = "Real Madrid",
            homeTeamCrestUrl = "rm.png",
            awayTeamId = 2,
            awayTeamName = "Barcelona",
            awayTeamCrestUrl = "barca.png",
            homeScore = null,
            awayScore = null
        )

        val beinChannel = LiveChannel(
            id = "bein_ch",
            name = "beIN Sports 1 HD",
            streamUrl = "http://test",
            logoUrl = "logo.png",
            categoryId = "sports",
            categoryName = "Sports",
            epgId = "bein_sports_1",
            channelNumber = 1
        )

        val skyChannel = LiveChannel(
            id = "sky_ch",
            name = "Sky Sports Main Event HD",
            streamUrl = "http://test",
            logoUrl = "logo.png",
            categoryId = "sports",
            categoryName = "Sports",
            epgId = "sky_sports_1",
            channelNumber = 2
        )

        // EPG on beIN channel
        val beinProgram = EpgProgramEntity(
            channelId = "bein_sports_1",
            title = "Real Madrid vs Barcelona Live",
            description = "El Clasico derby live match",
            startTime = 0L,
            endTime = 1000L
        )

        // EPG on Sky channel
        val skyProgram = EpgProgramEntity(
            channelId = "sky_sports_1",
            title = "Real Madrid vs Barcelona Live",
            description = "El Clasico derby live match",
            startTime = 0L,
            endTime = 1000L
        )

        // 1. Football match with matching EPG on beIN channel returns STRONG and matchedChannel.
        val beinOnlyChannels = listOf(beinChannel)
        val resultBeIn = FootballMatchUtils.matchMatchWithEpg(match, listOf(beinProgram), beinOnlyChannels)
        assertEquals("STRONG", resultBeIn.confidence)
        assertEquals("beIN Sports 1 HD", resultBeIn.matchedChannel?.name)

        // 2. Football match with matching EPG on non-beIN channel returns NONE when filtered.
        val resultSky = FootballMatchUtils.matchMatchWithEpg(match, listOf(skyProgram), beinOnlyChannels)
        assertEquals("NONE", resultSky.confidence)
        assertNull(resultSky.matchedChannel)

        // 3. If both beIN and non-beIN have matching EPG, and we apply the beIN filter, beIN is chosen.
        val epgProgramsInDb = listOf(skyProgram, beinProgram)
        // Under BEIN_ONLY mode, we only load EPG programs for the beIN channel IDs
        val filteredEpg = epgProgramsInDb.filter { it.channelId == "bein_sports_1" }
        val resultBoth = FootballMatchUtils.matchMatchWithEpg(match, filteredEpg, beinOnlyChannels)
        assertEquals("STRONG", resultBoth.confidence)
        assertEquals("beIN Sports 1 HD", resultBoth.matchedChannel?.name)

        // 4. If there are no beIN channels, football matches still appear but Watch button is hidden (confidence = NONE).
        val resultNoBeIn = FootballMatchUtils.matchMatchWithEpg(match, listOf(beinProgram), emptyList())
        assertEquals("NONE", resultNoBeIn.confidence)
        assertNull(resultNoBeIn.matchedChannel)
    }

    @Test
    fun testDaoGetEpgProgramsInWindowForChannels() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()

        try {
            val prog1 = EpgProgramEntity(
                channelId = "bein_sports_1",
                title = "Match 1",
                description = "Desc 1",
                startTime = 1000L,
                endTime = 2000L
            )
            val prog2 = EpgProgramEntity(
                channelId = "sky_sports_1",
                title = "Match 2",
                description = "Desc 2",
                startTime = 1000L,
                endTime = 2000L
            )
            dao.insertEpgPrograms(listOf(prog1, prog2))

            // Query only for beIN channel ID
            val results = dao.getEpgProgramsInWindowForChannels(500L, 2500L, listOf("bein_sports_1"))
            assertEquals(1, results.size)
            assertEquals("bein_sports_1", results[0].channelId)
            assertEquals("Match 1", results[0].title)
        } finally {
            database.close()
        }
    }

    class MockFootballApiClient(
        private val enabled: Boolean = true,
        private val matches: List<FootballMatch> = emptyList(),
        private val shouldThrow: Boolean = false
    ) : FootballApiClient() {
        override suspend fun getFootballSchedule(
            providerId: String,
            competitionCodes: String?,
            teamIds: String?
        ): FootballScheduleResponse {
            if (shouldThrow) {
                throw Exception("Simulated network failure")
            }
            return FootballScheduleResponse(
                providerId = "provider_1",
                enabled = enabled,
                range = null,
                matches = matches
            )
        }
    }

    @Test
    fun testIptvRepositoryFootballSchedule() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        val match1 = FootballMatch(
            matchId = 301,
            competitionCode = "PL",
            competitionName = "Premier League",
            competitionEmblemUrl = "pl.png",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            status = "SCHEDULED",
            matchday = 1,
            stage = "Regular",
            homeTeamId = 1,
            homeTeamName = "Real Madrid",
            homeTeamCrestUrl = "rm.png",
            awayTeamId = 2,
            awayTeamName = "Barcelona",
            awayTeamCrestUrl = "barca.png",
            homeScore = null,
            awayScore = null
        )

        val match2 = FootballMatch(
            matchId = 302,
            competitionCode = "LL",
            competitionName = "La Liga",
            competitionEmblemUrl = "ll.png",
            kickoffUtc = "2026-07-09T21:00:00.000Z",
            status = "SCHEDULED",
            matchday = 1,
            stage = "Regular",
            homeTeamId = 3,
            homeTeamName = "Liverpool",
            homeTeamCrestUrl = "lfc.png",
            awayTeamId = 4,
            awayTeamName = "Chelsea",
            awayTeamCrestUrl = "cfc.png",
            homeScore = null,
            awayScore = null
        )

        val beinChannel = LiveChannelEntity(
            id = "bein_ch",
            name = "beIN Sports 1 HD",
            streamUrl = "http://test",
            logoUrl = "logo.png",
            categoryId = "sports",
            categoryName = "Sports",
            epgId = "bein_sports_1",
            channelNumber = 1,
            isLocked = false,
            isAdult = false,
            hasCatchup = false,
            hidden = false,
            sortOrder = 1,
            updatedAt = System.currentTimeMillis()
        )

        val skyChannel = LiveChannelEntity(
            id = "sky_ch",
            name = "Sky Sports Main Event HD",
            streamUrl = "http://test",
            logoUrl = "logo.png",
            categoryId = "sports",
            categoryName = "Sports",
            epgId = "sky_sports_1",
            channelNumber = 2,
            isLocked = false,
            isAdult = false,
            hasCatchup = false,
            hidden = false,
            sortOrder = 2,
            updatedAt = System.currentTimeMillis()
        )

        val testKickoff = FootballMatchUtils.parseUtcToMillis(match1.kickoffUtc)
        val beinProgram = EpgProgramEntity(
            channelId = "bein_sports_1",
            title = "Real Madrid vs Barcelona Live",
            description = "El Clasico derby live match",
            startTime = testKickoff - 1000000L,
            endTime = testKickoff + 1000000L
        )

        try {
            // 1. When backend returns 2 matches and no channels exist, result size is 2 and both have confidence NONE.
            repository.testFootballApiClient = MockFootballApiClient(matches = listOf(match1, match2))
            val result1 = repository.getFootballSchedule("provider_1", listOf("PL", "LL"), emptyList())
            assertEquals(2, result1.size)
            assertEquals("NONE", result1[0].confidence)
            assertEquals("NONE", result1[1].confidence)

            // 2. When backend returns 2 matches and EPG query throws (simulated by a separate repository with closed DB), result size is still 2 and both have confidence NONE.
            val closedDb = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val closedDao = closedDb.iptvDao()
            val closedRepo = IptvRepository(closedDao, context)
            closedRepo.testFootballApiClient = MockFootballApiClient(matches = listOf(match1, match2))
            
            // Insert channels first
            closedDao.upsertLiveChannels(listOf(beinChannel))
            // Close the DB to make query fail
            closedDb.close()
            
            val result2 = closedRepo.getFootballSchedule("provider_1", listOf("PL", "LL"), emptyList())
            assertEquals(2, result2.size)
            assertEquals("NONE", result2[0].confidence)
            assertEquals("NONE", result2[1].confidence)

            // 3. When backend returns 2 matches and no beIN channels exist, result size is still 2 and both have confidence NONE.
            dao.upsertLiveChannels(listOf(skyChannel))
            val result3 = repository.getFootballSchedule("provider_1", listOf("PL", "LL"), emptyList())
            assertEquals(2, result3.size)
            assertEquals("NONE", result3[0].confidence)
            assertEquals("NONE", result3[1].confidence)

            // 4. When backend returns 0 matches, result is empty.
            repository.testFootballApiClient = MockFootballApiClient(matches = emptyList())
            val result4 = repository.getFootballSchedule("provider_1", listOf("PL"), emptyList())
            assertTrue(result4.isEmpty())

            // 5. When backend call fails, result is empty.
            repository.testFootballApiClient = MockFootballApiClient(shouldThrow = true)
            val result5 = repository.getFootballSchedule("provider_1", listOf("PL"), emptyList())
            assertTrue(result5.isEmpty())

            // 6. When beIN channel + matching EPG exist, result contains match with STRONG confidence and matchedChannel.
            dao.clearLiveChannels(null)
            dao.upsertLiveChannels(listOf(beinChannel, skyChannel))
            dao.insertEpgPrograms(listOf(beinProgram))
            
            repository.testFootballApiClient = MockFootballApiClient(matches = listOf(match1))
            val result6 = repository.getFootballSchedule("provider_1", listOf("PL"), emptyList())
            assertEquals(1, result6.size)
            assertEquals("STRONG", result6[0].confidence)
            assertEquals("bein_ch", result6[0].matchedChannel?.id)
            assertEquals("beIN Sports 1 HD", result6[0].matchedChannel?.name)

        } finally {
            database.close()
        }
    }
}
