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
        private val beinMatches: List<BeinFootballMatch> = emptyList(),
        private val watchMatches: List<FootballBroadcastMatch> = emptyList(),
        private val shouldThrow: Boolean = false
    ) : FootballApiClient("https://mock-url.com") {
        override suspend fun getBeinFootballSchedule(
            providerId: String,
            country: String
        ): BeinFootballScheduleResponse {
            if (shouldThrow) {
                throw Exception("Simulated network failure")
            }
            return BeinFootballScheduleResponse(
                providerId = "provider_1",
                enabled = enabled,
                source = "bein_guide",
                country = country,
                range = null,
                matches = beinMatches
            )
        }

        override suspend fun getFootballWatchSchedule(
            providerId: String,
            competitionCodes: String?,
            teamIds: String?,
            country: String
        ): FootballWatchScheduleResponse {
            if (shouldThrow) {
                throw Exception("Simulated network failure")
            }
            return FootballWatchScheduleResponse(
                providerId = "provider_1",
                enabled = enabled,
                source = "football_data_plus_bein_guide",
                country = country,
                range = null,
                matches = watchMatches
            )
        }
    }

    @Test
    fun testBeinFootballMatchCompatibilityMapping() {
        val match1 = BeinFootballMatch(
            id = "event_1",
            title = "Real Madrid vs Barcelona",
            competitionName = "La Liga",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            endUtc = "2026-07-09T21:00:00.000Z",
            status = "SCHEDULED",
            channelName = "beIN SPORTS 1",
            channelNumber = "1"
        )
        val compat1 = match1.toFootballMatchCompat()
        assertEquals(match1.id.hashCode(), compat1.matchId)
        assertEquals("La Liga", compat1.competitionName)
        assertEquals("Real Madrid", compat1.homeTeamName)
        assertEquals("Barcelona", compat1.awayTeamName)

        val match2 = BeinFootballMatch(
            id = null,
            title = "Chelsea v Arsenal",
            competitionName = "Premier League",
            kickoffUtc = "2026-07-09T15:00:00.000Z",
            endUtc = "2026-07-09T17:00:00.000Z",
            status = "LIVE",
            channelName = "beIN SPORTS 2",
            channelNumber = "2"
        )
        val compat2 = match2.toFootballMatchCompat()
        assertEquals("Chelsea", compat2.homeTeamName)
        assertEquals("Arsenal", compat2.awayTeamName)
        assertEquals("LIVE", compat2.status)

        val match3 = BeinFootballMatch(
            id = "event_3",
            title = "PSG - Lyon",
            competitionName = "Ligue 1",
            kickoffUtc = "2026-07-09T20:00:00.000Z",
            endUtc = "2026-07-09T22:00:00.000Z",
            status = null,
            channelName = "beIN SPORTS 3",
            channelNumber = "3"
        )
        val compat3 = match3.toFootballMatchCompat()
        assertEquals("PSG", compat3.homeTeamName)
        assertEquals("Lyon", compat3.awayTeamName)
        assertEquals("SCHEDULED", compat3.status)

        val match4 = BeinFootballMatch(
            id = "event_4",
            title = "Juventus Club Special Presentation",
            competitionName = "Serie A",
            kickoffUtc = "2026-07-09T18:00:00.000Z",
            endUtc = "2026-07-09T20:00:00.000Z",
            status = "SCHEDULED",
            channelName = "beIN SPORTS 4",
            channelNumber = "4"
        )
        val compat4 = match4.toFootballMatchCompat()
        assertEquals("Juventus Club Special Presentation", compat4.homeTeamName)
        assertNull(compat4.awayTeamName)
    }

    @Test
    fun testBeinChannelMatchingPriorityAndRules() {
        val localChannels = listOf(
            LiveChannel(id = "ch_bein1", name = "AR | beIN Sports 1 HD", streamUrl = "http://", logoUrl = "", categoryId = "", categoryName = "Sports", epgId = "bein_sports_1", channelNumber = 1),
            LiveChannel(id = "ch_bein2", name = "BEIN SPORTS 2 FHD", streamUrl = "http://", logoUrl = "", categoryId = "", categoryName = "Sports", epgId = "bein_sports_2", channelNumber = 2),
            LiveChannel(id = "ch_beinmax1", name = "beIN Sports Max 1", streamUrl = "http://", logoUrl = "", categoryId = "", categoryName = "Sports", epgId = "bein_max_1", channelNumber = 10),
            LiveChannel(id = "ch_bein4k", name = "beIN 4K UHD", streamUrl = "http://", logoUrl = "", categoryId = "", categoryName = "Sports", epgId = "bein_4k", channelNumber = 20),
            LiveChannel(id = "ch_sky", name = "Sky Sports 1", streamUrl = "http://", logoUrl = "", categoryId = "", categoryName = "Sports", epgId = "sky_sports_1", channelNumber = 100),
            LiveChannel(id = "ch_ssc", name = "SSC 1 HD", streamUrl = "http://", logoUrl = "", categoryId = "", categoryName = "Sports", epgId = "ssc_1", channelNumber = 101),
            LiveChannel(id = "ch_osn", name = "OSN Sports 1", streamUrl = "http://", logoUrl = "", categoryId = "", categoryName = "Sports", epgId = "osn_1", channelNumber = 102)
        )

        // 1. Exact or partial match with priority for regular/Max/4k types
        val match1 = FootballChannelMatcher.findLocalBeinChannelForGuideEvent("beIN SPORTS 1", "1", localChannels)
        assertNotNull(match1)
        assertEquals("ch_bein1", match1?.id)

        val match2 = FootballChannelMatcher.findLocalBeinChannelForGuideEvent("beIN SPORTS 2", "2", localChannels)
        assertNotNull(match2)
        assertEquals("ch_bein2", match2?.id)

        val matchMax1 = FootballChannelMatcher.findLocalBeinChannelForGuideEvent("beIN SPORTS MAX 1", "1", localChannels)
        assertNotNull(matchMax1)
        assertEquals("ch_beinmax1", matchMax1?.id)

        val match4k = FootballChannelMatcher.findLocalBeinChannelForGuideEvent("beIN 4K", null, localChannels)
        assertNotNull(match4k)
        assertEquals("ch_bein4k", match4k?.id)

        // 2. Non-beIN channels should never match
        val noMatchSky = FootballChannelMatcher.findLocalBeinChannelForGuideEvent("Sky Sports 1", "1", localChannels)
        assertNull(noMatchSky)

        val noMatchSSC = FootballChannelMatcher.findLocalBeinChannelForGuideEvent("SSC 1", "1", localChannels)
        assertNull(noMatchSSC)
    }

    @Test
    fun testIptvRepositoryFootballScheduleNew() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        val matchEvent = FootballBroadcastMatch(
            matchId = 12345,
            competitionCode = "PD",
            competitionName = "La Liga",
            competitionEmblemUrl = null,
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            status = "SCHEDULED",
            matchday = 1,
            stage = null,
            homeTeamId = 86,
            homeTeamName = "Real Madrid",
            homeTeamCrestUrl = null,
            awayTeamId = 81,
            awayTeamName = "Barcelona",
            awayTeamCrestUrl = null,
            homeScore = null,
            awayScore = null,
            beinChannelName = "beIN SPORTS 1",
            beinChannelNumber = "1",
            broadcastMatched = true
        )

        val matchEventNoBroadcast = FootballBroadcastMatch(
            matchId = 12346,
            competitionCode = "PD",
            competitionName = "La Liga",
            competitionEmblemUrl = null,
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            status = "SCHEDULED",
            matchday = 1,
            stage = null,
            homeTeamId = 86,
            homeTeamName = "Real Madrid",
            homeTeamCrestUrl = null,
            awayTeamId = 81,
            awayTeamName = "Barcelona",
            awayTeamCrestUrl = null,
            homeScore = null,
            awayScore = null,
            beinChannelName = null,
            beinChannelNumber = null,
            broadcastMatched = false
        )

        val beinChannel = LiveChannelEntity(
            id = "bein_ch1",
            name = "AR | beIN Sports 1 HD",
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

        try {
            // 1. Empty selected competitions/teams returns empty list.
            repository.testFootballApiClient = MockFootballApiClient(watchMatches = listOf(matchEvent))
            val resultEmpty = repository.getFootballSchedule("provider_1", emptyList(), emptyList())
            assertTrue(resultEmpty.isEmpty())

            // 2. Real match with beIN broadcast but empty local channels returns CHANNEL_NOT_FOUND
            val result1 = repository.getFootballSchedule("provider_1", listOf("PD"), emptyList())
            assertEquals(1, result1.size)
            assertEquals("CHANNEL_NOT_FOUND", result1[0].confidence)
            assertNull(result1[0].matchedChannel)
            assertEquals("beIN SPORTS 1", result1[0].matchedProgramName)

            // 3. Real match with matching local channel returns confidence "BEIN_GUIDE"
            dao.upsertLiveChannels(listOf(beinChannel))
            val result2 = repository.getFootballSchedule("provider_1", listOf("PD"), emptyList())
            assertEquals(1, result2.size)
            assertEquals("BEIN_GUIDE", result2[0].confidence)
            assertNotNull(result2[0].matchedChannel)
            assertEquals("bein_ch1", result2[0].matchedChannel?.id)

            // 4. Real match but no beIN broadcast yet returns BROADCAST_NOT_FOUND
            repository.testFootballApiClient = MockFootballApiClient(watchMatches = listOf(matchEventNoBroadcast))
            val resultNoBroadcast = repository.getFootballSchedule("provider_1", listOf("PD"), emptyList())
            assertEquals(1, resultNoBroadcast.size)
            assertEquals("BROADCAST_NOT_FOUND", resultNoBroadcast[0].confidence)
            assertNull(resultNoBroadcast[0].matchedChannel)
            assertNull(resultNoBroadcast[0].matchedProgramName)

            // 5. Backend return empty
            repository.testFootballApiClient = MockFootballApiClient(watchMatches = emptyList())
            val result3 = repository.getFootballSchedule("provider_1", listOf("PD"), emptyList())
            assertTrue(result3.isEmpty())

            // 6. Backend throw exception
            repository.testFootballApiClient = MockFootballApiClient(shouldThrow = true)
            val result4 = repository.getFootballSchedule("provider_1", listOf("PD"), emptyList())
            assertTrue(result4.isEmpty())

        } finally {
            database.close()
        }
    }
}
