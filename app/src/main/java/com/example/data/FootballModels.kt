package com.example.data

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// --- 1. Models ---

@JsonClass(generateAdapter = true)
data class FootballCompetition(
    @Json(name = "competitionCode") val competitionCode: String,
    @Json(name = "name") val name: String,
    @Json(name = "country") val country: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "emblemUrl") val emblemUrl: String?
)

@JsonClass(generateAdapter = true)
data class FootballTeam(
    @Json(name = "teamId") val teamId: Int,
    @Json(name = "name") val name: String,
    @Json(name = "shortName") val shortName: String?,
    @Json(name = "tla") val tla: String?,
    @Json(name = "crestUrl") val crestUrl: String?,
    @Json(name = "competitionCodes") val competitionCodes: List<String>?
)

@JsonClass(generateAdapter = true)
data class FootballOptionsResponse(
    @Json(name = "providerId") val providerId: String?,
    @Json(name = "enabled") val enabled: Boolean?,
    @Json(name = "competitions") val competitions: List<FootballCompetition>?,
    @Json(name = "teams") val teams: List<FootballTeam>?
)

@JsonClass(generateAdapter = true)
data class FootballMatch(
    @Json(name = "matchId") val matchId: Int,
    @Json(name = "competitionCode") val competitionCode: String?,
    @Json(name = "competitionName") val competitionName: String?,
    @Json(name = "competitionEmblemUrl") val competitionEmblemUrl: String?,
    @Json(name = "kickoffUtc") val kickoffUtc: String?, // e.g., "2026-07-09T19:00:00.000Z"
    @Json(name = "status") val status: String?,
    @Json(name = "matchday") val matchday: Int?,
    @Json(name = "stage") val stage: String?,
    @Json(name = "homeTeamId") val homeTeamId: Int?,
    @Json(name = "homeTeamName") val homeTeamName: String?,
    @Json(name = "homeTeamCrestUrl") val homeTeamCrestUrl: String?,
    @Json(name = "awayTeamId") val awayTeamId: Int?,
    @Json(name = "awayTeamName") val awayTeamName: String?,
    @Json(name = "awayTeamCrestUrl") val awayTeamCrestUrl: String?,
    @Json(name = "homeScore") val homeScore: Int?,
    @Json(name = "awayScore") val awayScore: Int?
)

@JsonClass(generateAdapter = true)
data class FootballScheduleResponse(
    @Json(name = "providerId") val providerId: String?,
    @Json(name = "enabled") val enabled: Boolean?,
    @Json(name = "range") val range: FootballRange?,
    @Json(name = "matches") val matches: List<FootballMatch>?
)

@JsonClass(generateAdapter = true)
data class FootballRange(
    @Json(name = "from") val from: String?,
    @Json(name = "to") val to: String?
)

// UI Wrapped Match model including EPG mapping results
data class FootballWatchMatch(
    val match: FootballMatch,
    val matchedChannel: LiveChannel?,
    val matchedProgramName: String?,
    val confidence: String // "STRONG", "MEDIUM", "NONE"
)

// --- 2. SharedPreferences Storage ---

class FootballPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("football_settings", Context.MODE_PRIVATE)

    var showFootballScheduleOnHome: Boolean
        get() = prefs.getBoolean("show_football_schedule", true)
        set(value) = prefs.edit().putBoolean("show_football_schedule", value).apply()

    var hasSeenFootballSetup: Boolean
        get() = prefs.getBoolean("has_seen_football_setup", false)
        set(value) = prefs.edit().putBoolean("has_seen_football_setup", value).apply()

    var selectedFootballCompetitionCodes: List<String>
        get() = prefs.getString("selected_competitions", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        set(value) = prefs.edit().putString("selected_competitions", value.joinToString(",")).apply()

    var selectedFootballTeamIds: List<Int>
        get() = prefs.getString("selected_teams", "")?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        set(value) = prefs.edit().putString("selected_teams", value.joinToString(",")).apply()
}

// --- 3. Normalization and Matching Utilities ---

object FootballMatchUtils {

    fun parseUtcToMillis(utcStr: String?): Long {
        if (utcStr.isNullOrBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(utcStr)?.time ?: 0L
        } catch (e: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(utcStr)?.time ?: 0L
            } catch (ex: Exception) {
                0L
            }
        }
    }

    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        var clean = temp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        clean = clean.lowercase()
        // Replace & with and
        clean = clean.replace("&", "and")
        // Remove punctuation
        clean = clean.replace(Regex("[^a-z0-9\\s]"), "")
        // Remove common suffixes: FC, CF, SC, AFC, Club
        val suffixes = listOf("fc", "cf", "sc", "afc", "club")
        for (s in suffixes) {
            clean = clean.replace(Regex("\\b$s\\b"), "")
        }
        // Collapse multiple spaces
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean
    }

    fun matchMatchWithEpg(
        match: FootballMatch,
        epgProgramsInWindow: List<EpgProgramEntity>,
        channels: List<LiveChannel>
    ): FootballWatchMatch {
        val homeNormalized = normalize(match.homeTeamName)
        val awayNormalized = normalize(match.awayTeamName)
        val compNormalized = normalize(match.competitionName)

        if (homeNormalized.isEmpty() || awayNormalized.isEmpty()) {
            return FootballWatchMatch(match, null, null, "NONE")
        }

        var bestProgram: EpgProgramEntity? = null
        var bestConfidence = "NONE"

        for (prog in epgProgramsInWindow) {
            val titleNorm = normalize(prog.title)
            val descNorm = normalize(prog.description)

            // Strong match: EPG title/description contains both home team and away team.
            val strongTitle = titleNorm.contains(homeNormalized) && titleNorm.contains(awayNormalized)
            val strongDesc = descNorm.contains(homeNormalized) && descNorm.contains(awayNormalized)

            if (strongTitle || strongDesc) {
                bestProgram = prog
                bestConfidence = "STRONG"
                break // Strongest possible match, stop search
            }

            // Medium match: EPG title/description contains one team and competition name.
            val hasHome = titleNorm.contains(homeNormalized) || descNorm.contains(homeNormalized)
            val hasAway = titleNorm.contains(awayNormalized) || descNorm.contains(awayNormalized)
            val hasComp = (compNormalized.isNotEmpty() && (titleNorm.contains(compNormalized) || descNorm.contains(compNormalized)))

            if ((hasHome || hasAway) && hasComp) {
                if (bestConfidence != "STRONG") {
                    bestProgram = prog
                    bestConfidence = "MEDIUM"
                }
            }
        }

        val matchedChannel = if (bestProgram != null) {
            channels.find { it.id == bestProgram.channelId }
        } else {
            null
        }

        return FootballWatchMatch(
            match = match,
            matchedChannel = matchedChannel,
            matchedProgramName = bestProgram?.title,
            confidence = if (matchedChannel != null) bestConfidence else "NONE"
        )
    }
}

// --- 4. API Client ---

interface FootballApiService {
    @GET("api/v1/football/options")
    suspend fun getFootballOptions(
        @Query("provider_id") providerId: String
    ): FootballOptionsResponse

    @GET("api/v1/football/schedule")
    suspend fun getFootballSchedule(
        @Query("provider_id") providerId: String,
        @Query("competition_codes") competitionCodes: String?,
        @Query("team_ids") teamIds: String?
    ): FootballScheduleResponse
}

class FootballApiClient(private val baseUrl: String = "https://iptv-football-backendn.onrender.com") {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.removeSuffix("/") + "/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: FootballApiService = retrofit.create(FootballApiService::class.java)

    suspend fun getFootballOptions(providerId: String): FootballOptionsResponse {
        return service.getFootballOptions(providerId)
    }

    suspend fun getFootballSchedule(
        providerId: String,
        competitionCodes: String?,
        teamIds: String?
    ): FootballScheduleResponse {
        return service.getFootballSchedule(providerId, competitionCodes, teamIds)
    }
}
