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
        val temp = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
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

    fun getTeamVariants(normalizedName: String): List<String> {
        val variants = mutableListOf(normalizedName)
        when (normalizedName) {
            "man city", "mancity" -> {
                variants.add("manchester city")
            }
            "manchester city" -> {
                variants.add("man city")
                variants.add("mancity")
            }
            "man united", "manutd", "man utd" -> {
                variants.add("manchester united")
            }
            "manchester united" -> {
                variants.add("man united")
                variants.add("man utd")
                variants.add("manutd")
            }
            "inter" -> {
                variants.add("internazionale")
                variants.add("inter milan")
            }
            "internazionale", "inter milan" -> {
                variants.add("inter")
            }
            "psg" -> {
                variants.add("paris saint germain")
                variants.add("paris saintgermain")
            }
            "paris saint germain", "paris saintgermain" -> {
                variants.add("psg")
            }
            "bayern" -> {
                variants.add("bayern munich")
                variants.add("bayern munchen")
            }
            "bayern munich", "bayern munchen" -> {
                variants.add("bayern")
            }
            "real madrid" -> {
                variants.add("real madrid cf")
                variants.add("real")
            }
            "barcelona" -> {
                variants.add("fc barcelona")
                variants.add("barca")
            }
            "barca" -> {
                variants.add("barcelona")
            }
        }
        return variants.distinct()
    }

    fun containsTeam(text: String, teamNormalized: String): Boolean {
        val variants = getTeamVariants(teamNormalized)
        return variants.any { variant -> text.contains(variant) }
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

            // Strong match: EPG title/description contains both home team and away team (and supporting aliases)
            val hasHome = containsTeam(titleNorm, homeNormalized) || containsTeam(descNorm, homeNormalized)
            val hasAway = containsTeam(titleNorm, awayNormalized) || containsTeam(descNorm, awayNormalized)
            val strongTitle = containsTeam(titleNorm, homeNormalized) && containsTeam(titleNorm, awayNormalized)
            val strongDesc = containsTeam(descNorm, homeNormalized) && containsTeam(descNorm, awayNormalized)
            val strongCross = (containsTeam(titleNorm, homeNormalized) && containsTeam(descNorm, awayNormalized)) ||
                              (containsTeam(descNorm, homeNormalized) && containsTeam(titleNorm, awayNormalized))

            if (strongTitle || strongDesc || strongCross) {
                bestProgram = prog
                bestConfidence = "STRONG"
                break // Strongest possible match, stop search
            }

            // Medium match: EPG title/description contains one team and competition name
            val hasComp = (compNormalized.isNotEmpty() && (titleNorm.contains(compNormalized) || descNorm.contains(compNormalized)))

            if ((hasHome || hasAway) && hasComp) {
                if (bestConfidence != "STRONG") {
                    bestProgram = prog
                    bestConfidence = "MEDIUM"
                }
            }
        }

        val matchedChannel = if (bestProgram != null) {
            val progChannelIdNorm = normalize(bestProgram.channelId)
            channels.find { channel ->
                channel.epgId.equals(bestProgram.channelId, ignoreCase = true) ||
                channel.id.equals(bestProgram.channelId, ignoreCase = true) ||
                normalize(channel.epgId) == progChannelIdNorm ||
                normalize(channel.id) == progChannelIdNorm
            }
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

@JsonClass(generateAdapter = true)
data class BeinFootballScheduleResponse(
    @Json(name = "providerId") val providerId: String?,
    @Json(name = "enabled") val enabled: Boolean?,
    @Json(name = "source") val source: String?,
    @Json(name = "country") val country: String?,
    @Json(name = "range") val range: FootballRange?,
    @Json(name = "matches") val matches: List<BeinFootballMatch>?
)

@JsonClass(generateAdapter = true)
data class BeinFootballMatch(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "competitionName") val competitionName: String?,
    @Json(name = "kickoffUtc") val kickoffUtc: String?,
    @Json(name = "endUtc") val endUtc: String?,
    @Json(name = "status") val status: String?,
    @Json(name = "channelName") val channelName: String?,
    @Json(name = "channelNumber") val channelNumber: String?
)

fun extractHomeTeamFromTitle(title: String?): String? {
    if (title == null) return null
    val delimiters = listOf(" vs ", " VS ", " v ", " V ", " - ")
    for (delim in delimiters) {
        if (title.contains(delim)) {
            val parts = title.split(delim, limit = 2)
            if (parts.isNotEmpty()) {
                return parts[0].trim()
            }
        }
    }
    return title.trim()
}

fun extractAwayTeamFromTitle(title: String?): String? {
    if (title == null) return null
    val delimiters = listOf(" vs ", " VS ", " v ", " V ", " - ")
    for (delim in delimiters) {
        if (title.contains(delim)) {
            val parts = title.split(delim, limit = 2)
            if (parts.size > 1) {
                return parts[1].trim()
            }
        }
    }
    return null
}

fun BeinFootballMatch.toFootballMatchCompat(): FootballMatch {
    return FootballMatch(
        matchId = id?.hashCode() ?: title?.hashCode() ?: 0,
        competitionCode = null,
        competitionName = competitionName,
        competitionEmblemUrl = null,
        kickoffUtc = kickoffUtc,
        status = status ?: "SCHEDULED",
        matchday = null,
        stage = null,
        homeTeamId = null,
        homeTeamName = extractHomeTeamFromTitle(title),
        homeTeamCrestUrl = null,
        awayTeamId = null,
        awayTeamName = extractAwayTeamFromTitle(title),
        awayTeamCrestUrl = null,
        homeScore = null,
        awayScore = null
    )
}

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

    @GET("api/v1/football/bein-schedule")
    suspend fun getBeinFootballSchedule(
        @Query("provider_id") providerId: String,
        @Query("country") country: String
    ): BeinFootballScheduleResponse
}

open class FootballApiClient(private val baseUrl: String) {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.removeSuffix("/") + "/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: FootballApiService = retrofit.create(FootballApiService::class.java)

    open suspend fun getFootballOptions(providerId: String): FootballOptionsResponse {
        return service.getFootballOptions(providerId)
    }

    open suspend fun getFootballSchedule(
        providerId: String,
        competitionCodes: String?,
        teamIds: String?
    ): FootballScheduleResponse {
        return service.getFootballSchedule(providerId, competitionCodes, teamIds)
    }

    open suspend fun getBeinFootballSchedule(
        providerId: String,
        country: String
    ): BeinFootballScheduleResponse {
        return service.getBeinFootballSchedule(providerId, country)
    }
}
