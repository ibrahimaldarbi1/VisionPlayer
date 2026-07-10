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
        context.getSharedPreferences(
            "football_settings",
            Context.MODE_PRIVATE
        ).edit().clear().commit()

        val prefs = FootballPrefs(context)

        assertFalse(prefs.hasSeenFootballCompetitionSetup)
        prefs.hasSeenFootballCompetitionSetup = true
        assertTrue(prefs.hasSeenFootballCompetitionSetup)

        assertTrue(prefs.selectedFootballCompetitionKeys.isEmpty())
        prefs.selectedFootballCompetitionKeys = listOf("premier_league", "la_liga")
        assertEquals(listOf("premier_league", "la_liga"), prefs.selectedFootballCompetitionKeys)
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
        private val competitions: List<FootballCompetitionPreference> = emptyList(),
        private val beinMatches: List<BeinFootballMatch> = emptyList(),
        private val shouldThrow: Boolean = false,
        private val delayMs: Long = 0L
    ) : FootballApiClient("https://mock-url.com") {

        var competitionRequestCount = 0

        override suspend fun getFootballCompetitions(
            providerId: String,
            country: String
        ): FootballCompetitionsResponse {
            competitionRequestCount++
            if (delayMs > 0L) {
                kotlinx.coroutines.delay(delayMs)
            }
            if (shouldThrow) {
                throw Exception("Simulated network failure")
            }
            return FootballCompetitionsResponse(
                providerId = "provider_1",
                enabled = enabled,
                country = country,
                competitions = competitions
            )
        }

        override suspend fun getBeinFootballSchedule(
            providerId: String,
            country: String,
            competitionKeys: String?
        ): BeinFootballScheduleResponse {
            if (delayMs > 0L) {
                kotlinx.coroutines.delay(delayMs)
            }
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
    }

    private fun createBeinMatch(
        id: String? = null,
        sourceMatchId: String? = null,
        title: String? = null,
        competitionKey: String? = null,
        competitionName: String? = null,
        homeTeamName: String? = null,
        awayTeamName: String? = null,
        kickoffUtc: String? = null,
        endUtc: String? = null,
        status: String? = null,
        channelName: String? = null,
        channelNumber: String? = null,
        channelCode: String? = null
    ) = BeinFootballMatch(
        id = id,
        sourceMatchId = sourceMatchId,
        title = title,
        competitionKey = competitionKey,
        competitionName = competitionName,
        homeTeamName = homeTeamName,
        awayTeamName = awayTeamName,
        kickoffUtc = kickoffUtc,
        endUtc = endUtc,
        status = status,
        channelName = channelName,
        channelNumber = channelNumber,
        channelCode = channelCode
    )

    @Test
    fun testBeinFootballMatchCompatibilityMapping() {
        val match1 = createBeinMatch(
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

        val match2 = createBeinMatch(
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

        val match3 = createBeinMatch(
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

        val match4 = createBeinMatch(
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

        val matchEvent = createBeinMatch(
            id = "12345",
            title = "Real Madrid vs Barcelona",
            competitionName = "La Liga",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            endUtc = "2026-07-09T21:00:00.000Z",
            status = "SCHEDULED",
            channelName = "beIN SPORTS 1",
            channelNumber = "1"
        )

        val matchEventNoBroadcast = createBeinMatch(
            id = "12346",
            title = "Real Madrid vs Barcelona",
            competitionName = "La Liga",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            endUtc = "2026-07-09T21:00:00.000Z",
            status = "SCHEDULED",
            channelName = null,
            channelNumber = null
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
            // 1. Empty selected competitions returns empty list.
            repository.testFootballApiClient = MockFootballApiClient(beinMatches = listOf(matchEvent))
            val resultEmpty = repository.getFootballSchedule("provider_1", emptyList())
            assertTrue(resultEmpty.isEmpty())

            // 2. Real match with beIN broadcast but empty local channels returns CHANNEL_NOT_FOUND
            val result1 = repository.getFootballSchedule("provider_1", listOf("la_liga"))
            assertEquals(1, result1.size)
            assertEquals("CHANNEL_NOT_FOUND", result1[0].confidence)
            assertNull(result1[0].matchedChannel)
            assertEquals("beIN SPORTS 1", result1[0].matchedProgramName)

            // 3. Real match with matching local channel returns confidence "BEIN_CHANNEL_MATCHED"
            dao.upsertLiveChannels(listOf(beinChannel))
            val result2 = repository.getFootballSchedule("provider_1", listOf("la_liga"))
            assertEquals(1, result2.size)
            assertEquals("BEIN_CHANNEL_MATCHED", result2[0].confidence)
            assertNotNull(result2[0].matchedChannel)
            assertEquals("bein_ch1", result2[0].matchedChannel?.id)

            // 4. Real match but no beIN broadcast yet returns CHANNEL_NOT_FOUND (since no channel matched)
            repository.testFootballApiClient = MockFootballApiClient(beinMatches = listOf(matchEventNoBroadcast))
            val resultNoBroadcast = repository.getFootballSchedule("provider_1", listOf("la_liga"))
            assertEquals(1, resultNoBroadcast.size)
            assertEquals("CHANNEL_NOT_FOUND", resultNoBroadcast[0].confidence)
            assertNull(resultNoBroadcast[0].matchedChannel)
            assertNull(resultNoBroadcast[0].matchedProgramName)

            // 5. Backend return empty
            repository.testFootballApiClient = MockFootballApiClient(beinMatches = emptyList())
            val result3 = repository.getFootballSchedule("provider_1", listOf("la_liga"))
            assertTrue(result3.isEmpty())

            // 6. Backend throw exception
            repository.testFootballApiClient = MockFootballApiClient(shouldThrow = true)
            var didThrow = false
            try {
                repository.getFootballSchedule("provider_1", listOf("la_liga"))
            } catch (e: Exception) {
                didThrow = true
            }
            assertTrue(didThrow)

        } finally {
            database.close()
        }
    }

    @Test
    fun testFootballScheduleDeduplicationAnd4KMatching() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        val matchVariant1 = createBeinMatch(
            id = "event_dup_1",
            sourceMatchId = "event_dup_1",
            title = "Liverpool vs Chelsea",
            competitionName = "Premier League",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            endUtc = "2026-07-09T21:00:00.000Z",
            status = "SCHEDULED",
            channelName = "beIN SPORTS 1",
            channelNumber = "1"
        )

        val matchVariant2 = createBeinMatch(
            id = "event_dup_1", // same sourceMatchId
            sourceMatchId = "event_dup_1",
            title = "Liverpool vs Chelsea",
            competitionName = "Premier League",
            kickoffUtc = "2026-07-09T19:00:00.000Z",
            endUtc = "2026-07-09T21:00:00.000Z",
            status = "SCHEDULED",
            channelName = "beIN 4K UHD",
            channelNumber = "4k"
        )

        val localBein1 = LiveChannelEntity(
            id = "ch_bein_1",
            name = "AR | beIN Sports 1",
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

        dao.upsertLiveChannels(listOf(localBein1))

        repository.testFootballApiClient = MockFootballApiClient(
            beinMatches = listOf(matchVariant2, matchVariant1)
        )

        val result = repository.getFootballSchedule("provider_1", listOf("Premier League"))
        assertEquals(1, result.size)
        assertEquals("BEIN_CHANNEL_MATCHED", result[0].confidence)
        assertEquals("ch_bein_1", result[0].matchedChannel?.id)
        assertEquals("beIN SPORTS 1", result[0].matchedProgramName)
    }

    // --- PROBLEM 6: Competition Cache Tests ---

    @Test
    fun testSaveCachedCompetitionsStoresKeyAndName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FootballPrefs(context)
        prefs.saveCachedCompetitions(listOf(
            FootballCompetitionPreference("la_liga", "La Liga"),
            FootballCompetitionPreference("premier_league", "Premier League")
        ))
        val cached = prefs.getCachedCompetitions()
        assertEquals(2, cached.size)
        assertEquals("la_liga", cached[0].competitionKey)
        assertEquals("La Liga", cached[0].name)
        assertEquals("premier_league", cached[1].competitionKey)
        assertEquals("Premier League", cached[1].name)
    }

    @Test
    fun testGetCachedCompetitionsRestoresSavedList() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FootballPrefs(context)
        prefs.saveCachedCompetitions(listOf(
            FootballCompetitionPreference("serie_a", "Serie A")
        ))
        val restored = prefs.getCachedCompetitions()
        assertEquals(1, restored.size)
        assertEquals("serie_a", restored[0].competitionKey)
        assertEquals("Serie A", restored[0].name)
    }

    @Test
    fun testCorruptJsonReturnsEmptyListAndDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sharedPrefs = context.getSharedPreferences("football_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("cached_football_competitions_v1", "invalid-json-string").commit()

        val prefs = FootballPrefs(context)
        val result = prefs.getCachedCompetitions()
        assertTrue(result.isEmpty())
    }

    @Test
    fun testCachedCompetitionsUpdatedAtIsStored() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FootballPrefs(context)
        val before = System.currentTimeMillis()
        prefs.saveCachedCompetitions(listOf(
            FootballCompetitionPreference("ligue_1", "Ligue 1")
        ))
        val updatedAt = prefs.cachedCompetitionsUpdatedAt
        assertTrue(updatedAt >= before)
    }

    @Test
    fun testFreshNonEmptyCacheReturnsImmediately() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("football_settings", Context.MODE_PRIVATE).edit().clear().commit()

        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        repository.footballPrefs.saveCachedCompetitions(listOf(
            FootballCompetitionPreference("la_liga", "La Liga")
        ))
        repository.footballPrefs.cachedCompetitionsUpdatedAt = System.currentTimeMillis()

        val mockClient = MockFootballApiClient(
            competitions = listOf(FootballCompetitionPreference("premier_league", "Premier League"))
        )
        repository.testFootballApiClient = mockClient

        val comps = repository.getFootballCompetitions("provider_1", forceRefresh = false)
        assertEquals(1, comps.size)
        assertEquals("la_liga", comps[0].competitionKey)
        assertEquals(0, mockClient.competitionRequestCount)
        database.close()
    }

    @Test
    fun testForceRefreshCallsApiEvenWhenCacheIsFresh() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("football_settings", Context.MODE_PRIVATE).edit().clear().commit()

        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        repository.footballPrefs.saveCachedCompetitions(listOf(
            FootballCompetitionPreference("la_liga", "La Liga")
        ))
        repository.footballPrefs.cachedCompetitionsUpdatedAt = System.currentTimeMillis()

        val mockClient = MockFootballApiClient(
            competitions = listOf(FootballCompetitionPreference("premier_league", "Premier League"))
        )
        repository.testFootballApiClient = mockClient

        val comps = repository.getFootballCompetitions("provider_1", forceRefresh = true)
        assertEquals(1, comps.size)
        assertEquals("premier_league", comps[0].competitionKey)
        assertEquals(1, mockClient.competitionRequestCount)
        database.close()
    }

    @Test
    fun testBackendFailureReturnsStaleCacheWhenExists() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("football_settings", Context.MODE_PRIVATE).edit().clear().commit()

        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        repository.footballPrefs.saveCachedCompetitions(listOf(
            FootballCompetitionPreference("la_liga", "La Liga")
        ))
        repository.footballPrefs.cachedCompetitionsUpdatedAt = System.currentTimeMillis() - 48 * 60 * 60 * 1000L

        repository.testFootballApiClient = MockFootballApiClient(shouldThrow = true)

        val comps = repository.getFootballCompetitions("provider_1", forceRefresh = false)
        assertEquals(1, comps.size)
        assertEquals("la_liga", comps[0].competitionKey)
        database.close()
    }

    @Test
    fun testBackendFailureReturnsEmptyListWhenNoCache() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("football_settings", Context.MODE_PRIVATE).edit().clear().commit()

        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        repository.testFootballApiClient = MockFootballApiClient(shouldThrow = true)

        val comps = repository.getFootballCompetitions("provider_1", forceRefresh = false)
        assertTrue(comps.isEmpty())
        database.close()
    }

    // --- PROBLEM 7: Schedule Error Tests ---

    @Test
    fun testSuccessfulBackendResponseWithMatchesReturnsMatches() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        val match = createBeinMatch(
            id = "event_1",
            title = "Liverpool vs Chelsea",
            competitionName = "Premier League"
        )
        repository.testFootballApiClient = MockFootballApiClient(beinMatches = listOf(match))

        val result = repository.getFootballSchedule("provider_1", listOf("Premier League"))
        assertEquals(1, result.size)
        assertEquals("event_1".hashCode(), result[0].match.matchId)
        database.close()
    }

    @Test
    fun testSuccessfulBackendResponseWithEmptyMatchesReturnsEmptyList() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        repository.testFootballApiClient = MockFootballApiClient(beinMatches = emptyList())

        val result = repository.getFootballSchedule("provider_1", listOf("Premier League"))
        assertTrue(result.isEmpty())
        database.close()
    }

    @Test
    fun testBackendExceptionIsPropagatedToCaller() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        repository.testFootballApiClient = MockFootballApiClient(shouldThrow = true)

        var didThrow = false
        try {
            repository.getFootballSchedule("provider_1", listOf("Premier League"))
        } catch (e: Exception) {
            didThrow = true
        }
        assertTrue(didThrow)
        database.close()
    }

    @Test
    fun testScheduleTimeoutIsPropagatedToCaller() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        repository.footballScheduleTimeoutMs = 50L
        repository.testFootballApiClient = MockFootballApiClient(delayMs = 100L)

        var didThrowTimeout = false
        try {
            repository.getFootballSchedule("provider_1", listOf("Premier League"))
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            didThrowTimeout = true
        }
        assertTrue(didThrowTimeout)
        database.close()
    }

    @Test
    fun testLocalChannelCacheEmptyDoesNotRemoveBackendMatches() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        val match = createBeinMatch(
            id = "event_1",
            title = "Liverpool vs Chelsea",
            competitionName = "Premier League"
        )
        repository.testFootballApiClient = MockFootballApiClient(beinMatches = listOf(match))

        val result = repository.getFootballSchedule("provider_1", listOf("Premier League"))
        assertEquals(1, result.size)
        assertEquals("event_1".hashCode(), result[0].match.matchId)
        assertEquals("CHANNEL_NOT_FOUND", result[0].confidence)
        assertNull(result[0].matchedChannel)
        database.close()
    }

    @Test
    fun testDuplicateBroadcastVariantsDeduplicateExplicit() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        val match1 = createBeinMatch(
            id = "event_dup_1",
            sourceMatchId = "event_dup_1",
            title = "Liverpool vs Chelsea",
            competitionName = "Premier League",
            channelName = "beIN SPORTS 1"
        )
        val match2 = createBeinMatch(
            id = "event_dup_2",
            sourceMatchId = "event_dup_1",
            title = "Liverpool vs Chelsea",
            competitionName = "Premier League",
            channelName = "beIN SPORTS 2"
        )
        repository.testFootballApiClient = MockFootballApiClient(beinMatches = listOf(match1, match2))

        val result = repository.getFootballSchedule("provider_1", listOf("Premier League"))
        assertEquals(1, result.size)
        database.close()
    }

    @Test
    fun testMappedLocalChannelVariantIsPreferredExplicit() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.iptvDao()
        val repository = IptvRepository(dao, context)

        val match1 = createBeinMatch(
            id = "event_dup_1",
            sourceMatchId = "event_dup_1",
            title = "Liverpool vs Chelsea",
            competitionName = "Premier League",
            channelName = "beIN SPORTS 1",
            channelNumber = "1"
        )
        val match2 = createBeinMatch(
            id = "event_dup_2",
            sourceMatchId = "event_dup_1",
            title = "Liverpool vs Chelsea",
            competitionName = "Premier League",
            channelName = "beIN SPORTS 2",
            channelNumber = "2"
        )

        val localCh2 = LiveChannelEntity(
            id = "ch_bein_2",
            name = "AR | beIN Sports 2",
            streamUrl = "http://test",
            logoUrl = "logo.png",
            categoryId = "sports",
            categoryName = "Sports",
            epgId = "bein_sports_2",
            channelNumber = 2,
            isLocked = false,
            isAdult = false,
            hasCatchup = false,
            hidden = false,
            sortOrder = 2,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertLiveChannels(listOf(localCh2))

        repository.testFootballApiClient = MockFootballApiClient(beinMatches = listOf(match1, match2))

        val result = repository.getFootballSchedule("provider_1", listOf("Premier League"))
        assertEquals(1, result.size)
        assertEquals("BEIN_CHANNEL_MATCHED", result[0].confidence)
        assertEquals("ch_bein_2", result[0].matchedChannel?.id)
        database.close()
    }

    // --- PROBLEM 8: Feature Flag Visibility Coverage ---

    @Test
    fun testVisibilityDecisions() {
        assertFalse(com.example.data.FootballVisibilityHelper.shouldShowFootballFeature(featureEnabled = false, userEnabled = true))
        assertFalse(com.example.data.FootballVisibilityHelper.shouldShowFootballFeature(featureEnabled = true, userEnabled = false))
        assertTrue(com.example.data.FootballVisibilityHelper.shouldShowFootballFeature(featureEnabled = true, userEnabled = true))
        assertFalse(com.example.data.FootballVisibilityHelper.shouldShowFootballFeature(featureEnabled = false, userEnabled = false))
    }

    @Test
    fun testFootballChannelSnapshotReturnsOnlyBeinCandidates() =
        kotlinx.coroutines.runBlocking {
            val context =
                ApplicationProvider
                    .getApplicationContext<Context>()

            val database =
                androidx.room.Room
                    .inMemoryDatabaseBuilder(
                        context,
                        IptvDatabase::class.java
                    )
                    .allowMainThreadQueries()
                    .build()

            val dao =
                database.iptvDao()

            val now =
                System.currentTimeMillis()

            val entities =
                (1..1000).map {
                    index ->
                    LiveChannelEntity(
                        id =
                            "ordinary_$index",

                        name =
                            "Ordinary Channel $index",

                        streamUrl =
                            "https://example.invalid/$index",

                        logoUrl =
                            "",

                        categoryId =
                            "general",

                        categoryName =
                            "General",

                        epgId =
                            "ordinary_$index",

                        channelNumber =
                            index,

                        isLocked =
                            false,

                        isAdult =
                            false,

                        hasCatchup =
                            false,

                        hidden =
                            false,

                        sortOrder =
                            index,

                        updatedAt =
                            now
                    )
                } + LiveChannelEntity(
                    id =
                        "bein_1",

                    name =
                        "AR | beIN SPORTS 1 HD",

                    streamUrl =
                        "https://example.invalid/bein",

                    logoUrl =
                        "",

                    categoryId =
                        "sports",

                    categoryName =
                        "Sports",

                    epgId =
                        "bein.sports.1",

                    channelNumber =
                        5001,

                    isLocked =
                        false,

                    isAdult =
                        false,

                    hasCatchup =
                        false,

                    hidden =
                        false,

                    sortOrder =
                        5001,

                    updatedAt =
                        now
                )

            dao.upsertLiveChannels(
                entities
            )

            val repository =
                IptvRepository(
                    dao,
                    context
                )

            val result =
                repository
                    .getCachedLiveChannelsForFootballMapping()

            assertEquals(
                1,
                result.size
            )

            assertEquals(
                "bein_1",
                result.first().id
            )

            database.close()
        }
}
