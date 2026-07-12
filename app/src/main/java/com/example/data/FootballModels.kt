package com.example.data

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// --- 1. Models ---

@JsonClass(generateAdapter = true)
data class FootballCompetitionPreference(
    @Json(name = "competitionKey") val competitionKey: String,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class FootballCompetitionsResponse(
    @Json(name = "providerId") val providerId: String?,
    @Json(name = "enabled") val enabled: Boolean?,
    @Json(name = "country") val country: String?,
    @Json(name = "competitions") val competitions: List<FootballCompetitionPreference>?
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

    var selectedFootballCompetitionKeys: List<String>
        get() = prefs.getString("selected_competition_keys_v2", "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(
                "selected_competition_keys_v2",
                value
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .joinToString(",")
            )
            .apply()

    var hasSeenFootballCompetitionSetup: Boolean
        get() = prefs.getBoolean("has_seen_football_competition_setup_v2", false)
        set(value) = prefs.edit().putBoolean("has_seen_football_competition_setup_v2", value).apply()

    fun saveCachedCompetitions(competitions: List<FootballCompetitionPreference>) {
        try {
            val array = org.json.JSONArray()
            for (comp in competitions) {
                val obj = org.json.JSONObject()
                obj.put("competitionKey", comp.competitionKey)
                obj.put("name", comp.name)
                array.put(obj)
            }
            prefs.edit()
                .putString("cached_football_competitions_v1", array.toString())
                .putLong("cached_football_competitions_updated_at_v1", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("FootballPrefs", "Failed to save cached competitions", e)
        }
    }

    fun getCachedCompetitions(): List<FootballCompetitionPreference> {
        val jsonStr = prefs.getString("cached_football_competitions_v1", null) ?: return emptyList()
        try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<FootballCompetitionPreference>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val key = obj.optString("competitionKey") ?: continue
                val name = obj.optString("name") ?: continue
                if (key.isNotEmpty() && name.isNotEmpty()) {
                    list.add(FootballCompetitionPreference(key, name))
                }
            }
            return list
        } catch (e: Exception) {
            android.util.Log.e("FootballPrefs", "Failed to parse cached competitions", e)
            return emptyList()
        }
    }

    var cachedCompetitionsUpdatedAt: Long
        get() = prefs.getLong("cached_football_competitions_updated_at_v1", 0L)
        set(value) = prefs.edit().putLong("cached_football_competitions_updated_at_v1", value).apply()
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
    @Json(name = "sourceMatchId") val sourceMatchId: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "competitionKey") val competitionKey: String?,
    @Json(name = "competitionName") val competitionName: String?,
    @Json(name = "homeTeamName") val homeTeamName: String?,
    @Json(name = "awayTeamName") val awayTeamName: String?,
    @Json(name = "kickoffUtc") val kickoffUtc: String?,
    @Json(name = "endUtc") val endUtc: String?,
    @Json(name = "status") val status: String?,
    @Json(name = "channelName") val channelName: String?,
    @Json(name = "channelNumber") val channelNumber: String?,
    @Json(name = "channelCode") val channelCode: String?
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
        matchId = sourceMatchId?.hashCode()
            ?: id?.hashCode()
            ?: title?.hashCode()
            ?: 0,
        competitionCode = competitionKey,
        competitionName = competitionName,
        competitionEmblemUrl = null,
        kickoffUtc = kickoffUtc,
        status = status ?: "SCHEDULED",
        matchday = null,
        stage = null,
        homeTeamId = null,
        homeTeamName = homeTeamName ?: extractHomeTeamFromTitle(title),
        homeTeamCrestUrl = null,
        awayTeamId = null,
        awayTeamName = awayTeamName ?: extractAwayTeamFromTitle(title),
        awayTeamCrestUrl = null,
        homeScore = null,
        awayScore = null
    )
}

interface FootballApiService {
    @GET("api/v1/football/competitions")
    suspend fun getFootballCompetitions(
        @Query("provider_id") providerId: String,
        @Query("country") country: String
    ): FootballCompetitionsResponse

    @GET("api/v1/football/bein-schedule")
    suspend fun getBeinFootballSchedule(
        @Query("provider_id") providerId: String,
        @Query("country") country: String,
        @Query("competition_keys") competitionKeys: String?
    ): BeinFootballScheduleResponse
}

open class FootballApiClient(private val baseUrl: String) {
    val service: FootballApiService = com.example.core.network.BackendApiFactory.getFootballApiService(baseUrl)

    open suspend fun getFootballCompetitions(
        providerId: String,
        country: String
    ): FootballCompetitionsResponse {
        return try {
            com.example.core.network.NetworkRetryPolicy.retryWithBackoff {
                try {
                    service.getFootballCompetitions(providerId, country)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    throw com.example.core.network.mapThrowableToNetworkError(e)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw if (e is com.example.core.network.NetworkError) e else com.example.core.network.mapThrowableToNetworkError(e)
        }
    }

    open suspend fun getBeinFootballSchedule(
        providerId: String,
        country: String,
        competitionKeys: String?
    ): BeinFootballScheduleResponse {
        return try {
            com.example.core.network.NetworkRetryPolicy.retryWithBackoff {
                try {
                    service.getBeinFootballSchedule(providerId, country, competitionKeys)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    throw com.example.core.network.mapThrowableToNetworkError(e)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw if (e is com.example.core.network.NetworkError) e else com.example.core.network.mapThrowableToNetworkError(e)
        }
    }
}

object FootballVisibilityHelper {
    fun shouldShowFootballFeature(
        featureEnabled: Boolean,
        userEnabled: Boolean
    ): Boolean {
        return featureEnabled && userEnabled
    }
}

