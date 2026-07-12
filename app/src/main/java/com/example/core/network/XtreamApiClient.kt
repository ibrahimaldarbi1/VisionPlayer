package com.example.core.network

import com.example.data.*
import com.squareup.moshi.JsonReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import com.example.core.redaction.SensitiveDataRedactor

class XtreamApiClient(private val client: OkHttpClient) {

    private fun nextStringOrNumber(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> {
                try {
                    reader.nextLong().toString()
                } catch (e: Exception) {
                    try {
                        reader.nextDouble().toString()
                    } catch (ex: Exception) {
                        reader.skipValue()
                        ""
                    }
                }
            }
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            JsonReader.Token.NULL -> {
                reader.nextNull<Any?>()
                ""
            }
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private suspend fun <T> executeRequest(urlString: String, block: (ResponseBody) -> T): T {
        return try {
            NetworkRetryPolicy.retryWithBackoff {
                val request = Request.Builder()
                    .url(urlString)
                    .build()
                val response = try {
                    client.newCall(request).execute()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    throw mapThrowableToNetworkError(e)
                }
                
                response.use { resp ->
                    val error = mapResponseCodeToNetworkError(resp.code)
                    if (error != null) {
                        throw error
                    }
                    val body = resp.body ?: throw NetworkError.InvalidResponse
                    block(body)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            val redactedUrl = SensitiveDataRedactor.redactUrl(urlString)
            val redactedMsg = SensitiveDataRedactor.redactExceptionMessage(e.message)
            android.util.Log.e("XtreamApiClient", "Request failed for $redactedUrl: $redactedMsg")
            
            if (e is NetworkError) {
                throw e
            } else {
                throw NetworkError.Unknown(e)
            }
        }
    }

    suspend fun makeHttpGetRequest(urlString: String): String {
        return executeRequest(urlString) { body ->
            body.string()
        }
    }

    suspend fun fetchXtreamCategories(session: SessionEntity, type: String): List<Category> {
        val action = when (type) {
            "LIVE" -> "get_live_categories"
            "MOVIE" -> "get_vod_categories"
            "SERIES" -> "get_series_categories"
            else -> return emptyList()
        }
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=$action"
        return executeRequest(urlString) { body ->
            val categories = mutableListOf<Category>()
            try {
                val jsonArray = org.json.JSONArray(body.string())
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("category_id")
                    val name = obj.optString("category_name")
                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        categories.add(Category(id = id, name = name, type = type))
                    }
                }
            } catch (e: Exception) {
                throw NetworkError.InvalidResponse
            }
            categories
        }
    }

    suspend fun fetchXtreamLiveChannels(session: SessionEntity, categoryId: String?, streamFormat: String = "TS"): List<LiveChannel> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_live_streams" +
                if (categoryId != null) "&category_id=$categoryId" else ""
        
        return executeRequest(urlString) { body ->
            val channels = mutableListOf<LiveChannel>()
            try {
                val reader = JsonReader.of(body.source())
                reader.beginArray()
                var index = 0
                while (reader.hasNext()) {
                    reader.beginObject()
                    var streamId = ""
                    var name = ""
                    var logo = ""
                    var catId = ""
                    var epgId = ""
                    var num = -1
                    var haveCatchup = 0
                    
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (reader.peek() == JsonReader.Token.NULL) {
                            reader.nextNull<Any?>()
                            continue
                        }
                        when (key) {
                            "stream_id" -> streamId = nextStringOrNumber(reader)
                            "name" -> name = reader.nextString()
                            "stream_icon" -> logo = reader.nextString()
                            "category_id" -> catId = nextStringOrNumber(reader)
                            "epg_channel_id" -> epgId = reader.nextString()
                            "num" -> num = reader.nextInt()
                            "have_catchup" -> haveCatchup = reader.nextInt()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    index++
                    
                    if (streamId.isNotEmpty()) {
                        val ext = if (streamFormat.uppercase() == "M3U8") "m3u8" else "ts"
                        val streamUrl = "${session.serverUrl}/live/${session.username}/${session.token}/$streamId.$ext"
                        val finalEpgId = if (epgId.isEmpty()) "epg_$streamId" else epgId
                        val finalNum = if (num == -1) index else num
                        
                        channels.add(
                            LiveChannel(
                                id = streamId,
                                name = name,
                                streamUrl = streamUrl,
                                logoUrl = logo,
                                categoryId = catId,
                                categoryName = "Live Channel",
                                epgId = finalEpgId,
                                channelNumber = finalNum,
                                hasCatchup = haveCatchup == 1
                            )
                        )
                    }
                }
                reader.endArray()
            } catch (e: Exception) {
                throw NetworkError.InvalidResponse
            }
            channels
        }
    }

    suspend fun fetchXtreamMovies(session: SessionEntity, categoryId: String?): List<Movie> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_vod_streams" +
                if (categoryId != null) "&category_id=$categoryId" else ""
        
        return executeRequest(urlString) { body ->
            val movies = mutableListOf<Movie>()
            try {
                val reader = JsonReader.of(body.source())
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginObject()
                    var streamId = ""
                    var name = ""
                    var logo = ""
                    var catId = ""
                    var rating = "8.0"
                    var ext = "mp4"
                    var year = "2024"
                    
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (reader.peek() == JsonReader.Token.NULL) {
                            reader.nextNull<Any?>()
                            continue
                        }
                        when (key) {
                            "stream_id" -> streamId = nextStringOrNumber(reader)
                            "name" -> name = reader.nextString()
                            "stream_icon" -> logo = reader.nextString()
                            "category_id" -> catId = nextStringOrNumber(reader)
                            "rating" -> rating = nextStringOrNumber(reader)
                            "container_extension" -> ext = reader.nextString()
                            "year" -> year = nextStringOrNumber(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    
                    if (streamId.isNotEmpty()) {
                        val finalExt = if (ext.isEmpty() || ext == "null") "mp4" else ext
                        val streamUrl = "${session.serverUrl}/movie/${session.username}/${session.token}/$streamId.$finalExt"
                        val finalRating = if (rating.isEmpty()) "8.0" else rating
                        val finalYear = if (year.isEmpty()) "2024" else year
                        
                        movies.add(
                            Movie(
                                id = streamId,
                                title = name,
                                streamUrl = streamUrl,
                                posterUrl = logo,
                                backdropUrl = logo,
                                categoryId = catId,
                                categoryName = "Movie",
                                description = "Premium legal video stream provided by your connected server credentials.",
                                rating = finalRating,
                                year = finalYear
                            )
                        )
                    }
                }
                reader.endArray()
            } catch (e: Exception) {
                throw NetworkError.InvalidResponse
            }
            movies
        }
    }

    suspend fun fetchXtreamSeries(session: SessionEntity, categoryId: String?): List<Series> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_series" +
                if (categoryId != null) "&category_id=$categoryId" else ""
        
        return executeRequest(urlString) { body ->
            val seriesList = mutableListOf<Series>()
            try {
                val reader = JsonReader.of(body.source())
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginObject()
                    var seriesId = ""
                    var name = ""
                    var logo = ""
                    var catId = ""
                    var rating = "8.0"
                    var releaseDate = "2024"
                    
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (reader.peek() == JsonReader.Token.NULL) {
                            reader.nextNull<Any?>()
                            continue
                        }
                        when (key) {
                            "series_id" -> seriesId = nextStringOrNumber(reader)
                            "name" -> name = reader.nextString()
                            "cover" -> logo = reader.nextString()
                            "category_id" -> catId = nextStringOrNumber(reader)
                            "rating" -> rating = nextStringOrNumber(reader)
                            "releaseDate" -> releaseDate = reader.nextString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    
                    if (seriesId.isNotEmpty()) {
                        val finalRating = if (rating.isEmpty()) "8.0" else rating
                        val finalYear = if (releaseDate.isEmpty()) "2024" else releaseDate.take(4)
                        
                        seriesList.add(
                            Series(
                                id = seriesId,
                                title = name,
                                posterUrl = logo,
                                backdropUrl = logo,
                                categoryId = catId,
                                categoryName = "Series",
                                description = "Full TV Show provided by your secure server login. Browse seasons and episodes below.",
                                rating = finalRating,
                                year = finalYear
                            )
                        )
                    }
                }
                reader.endArray()
            } catch (e: Exception) {
                throw NetworkError.InvalidResponse
            }
            seriesList
        }
    }

    suspend fun fetchXtreamSeasons(session: SessionEntity, seriesId: String): List<Season> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_series_info&series_id=$seriesId"
        return executeRequest(urlString) { body ->
            val response = body.string()
            val seasons = mutableListOf<Season>()
            try {
                val trimResponse = response.trim()
                if (trimResponse.startsWith("[")) {
                    val epArray = org.json.JSONArray(trimResponse)
                    val uniqueSeasons = mutableSetOf<Int>()
                    for (i in 0 until epArray.length()) {
                        val obj = epArray.getJSONObject(i)
                        val seasonNum = obj.optInt("season", obj.optInt("season_number", 1))
                        uniqueSeasons.add(seasonNum)
                    }
                    for (seasonNum in uniqueSeasons) {
                        seasons.add(
                            Season(
                                id = "${seriesId}_s$seasonNum",
                                seriesId = seriesId,
                                seasonNumber = seasonNum,
                                title = "Season $seasonNum"
                            )
                        )
                    }
                } else {
                    val root = org.json.JSONObject(trimResponse)
                    val seasonsArray = root.optJSONArray("seasons")
                    if (seasonsArray != null && seasonsArray.length() > 0) {
                        for (i in 0 until seasonsArray.length()) {
                            val element = seasonsArray.get(i)
                            if (element is org.json.JSONObject) {
                                val seasonNum = element.optInt("season_number", i + 1)
                                val name = element.optString("name", "Season $seasonNum")
                                seasons.add(
                                    Season(
                                        id = "${seriesId}_s$seasonNum",
                                        seriesId = seriesId,
                                        seasonNumber = seasonNum,
                                        title = name
                                    )
                                )
                            } else {
                                val name = element.toString()
                                val seasonNum = i + 1
                                seasons.add(
                                    Season(
                                        id = "${seriesId}_s$seasonNum",
                                        seriesId = seriesId,
                                        seasonNumber = seasonNum,
                                        title = name
                                    )
                                )
                            }
                        }
                    } else {
                        val seasonsObj = root.optJSONObject("seasons")
                        if (seasonsObj != null) {
                            val keys = seasonsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val seasonNum = key.toIntOrNull() ?: (seasonsObj.optJSONObject(key)?.optInt("season_number") ?: 1)
                                val obj = seasonsObj.optJSONObject(key)
                                val name = obj?.optString("name", "Season $seasonNum") ?: "Season $seasonNum"
                                seasons.add(
                                    Season(
                                        id = "${seriesId}_s$seasonNum",
                                        seriesId = seriesId,
                                        seasonNumber = seasonNum,
                                        title = name
                                    )
                                )
                            }
                        }
                    }
                    
                    if (seasons.isEmpty()) {
                        val episodesObj = root.optJSONObject("episodes")
                        if (episodesObj != null) {
                            val keys = episodesObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val seasonNum = key.toIntOrNull() ?: 1
                                seasons.add(
                                    Season(
                                        id = "${seriesId}_s$seasonNum",
                                        seriesId = seriesId,
                                        seasonNumber = seasonNum,
                                        title = "Season $seasonNum"
                                    )
                                )
                            }
                        } else {
                            val episodesArray = root.optJSONArray("episodes")
                            if (episodesArray != null) {
                                val uniqueSeasons = mutableSetOf<Int>()
                                for (i in 0 until episodesArray.length()) {
                                   val obj = episodesArray.getJSONObject(i)
                                    val seasonNum = obj.optInt("season", obj.optInt("season_number", 1))
                                    uniqueSeasons.add(seasonNum)
                                }
                                for (seasonNum in uniqueSeasons) {
                                    seasons.add(
                                        Season(
                                            id = "${seriesId}_s$seasonNum",
                                            seriesId = seriesId,
                                            seasonNumber = seasonNum,
                                            title = "Season $seasonNum"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                throw NetworkError.InvalidResponse
            }
            seasons.sortedBy { it.seasonNumber }
        }
    }

    private fun getEpisodeId(obj: org.json.JSONObject): String {
        val idKeys = listOf("episode_id", "movie_id", "stream_id", "id")
        for (key in idKeys) {
            val value = obj.optString(key)
            if (!value.isNullOrEmpty() && value != "null") {
                return value
            }
        }
        return ""
    }

    private fun getEpisodeExtension(obj: org.json.JSONObject): String {
        val extKeys = listOf("container_extension", "extension", "ext")
        for (key in extKeys) {
            val value = obj.optString(key)
            if (!value.isNullOrEmpty() && value != "null") {
                return value
            }
        }
        return "mp4"
    }

    suspend fun fetchXtreamEpisodes(session: SessionEntity, seriesId: String, seasonId: String): List<Episode> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_series_info&series_id=$seriesId"
        return executeRequest(urlString) { body ->
            val response = body.string()
            val episodes = mutableListOf<Episode>()
            try {
                val trimResponse = response.trim()
                val seasonNum = seasonId.substringAfterLast("_s").toIntOrNull() ?: 1
                
                if (trimResponse.startsWith("[")) {
                    val epArray = org.json.JSONArray(trimResponse)
                    var epNumCounter = 1
                    for (i in 0 until epArray.length()) {
                        val obj = epArray.getJSONObject(i)
                        val epSeason = obj.optInt("season", obj.optInt("season_number", 1))
                        if (epSeason == seasonNum) {
                            val id = getEpisodeId(obj)
                            if (id.isEmpty()) continue
                            val epNum = obj.optInt("episode_num", epNumCounter++)
                            val title = obj.optString("title", "Episode $epNum")
                            val ext = getEpisodeExtension(obj)
                            
                            val streamUrl = "${session.serverUrl}/series/${session.username}/${session.token}/$id.$ext"
                            val info = obj.optJSONObject("info")
                            val desc = info?.optString("plot") ?: "No description available."
                            val duration = info?.optString("duration", "45m") ?: "45m"
                            
                            episodes.add(
                                Episode(
                                    id = id,
                                    seriesId = seriesId,
                                    seasonId = seasonId,
                                    seasonNumber = seasonNum,
                                    episodeNumber = epNum,
                                    title = title,
                                    streamUrl = streamUrl,
                                    description = desc,
                                    duration = duration
                                )
                            )
                        }
                    }
                } else {
                    val root = org.json.JSONObject(trimResponse)
                    val episodesObj = root.optJSONObject("episodes")
                    if (episodesObj != null) {
                        val epArray = episodesObj.optJSONArray(seasonNum.toString())
                        if (epArray != null) {
                            for (i in 0 until epArray.length()) {
                                val obj = epArray.getJSONObject(i)
                                val id = getEpisodeId(obj)
                                if (id.isEmpty()) continue
                                val epNum = obj.optInt("episode_num", i + 1)
                                val title = obj.optString("title", "Episode $epNum")
                                val ext = getEpisodeExtension(obj)
                                val streamUrl = "${session.serverUrl}/series/${session.username}/${session.token}/$id.$ext"
                                val info = obj.optJSONObject("info")
                                val desc = info?.optString("plot") ?: "No description available."
                                val duration = info?.optString("duration", "45m") ?: "45m"
                                
                                episodes.add(
                                    Episode(
                                        id = id,
                                        seriesId = seriesId,
                                        seasonId = seasonId,
                                        seasonNumber = seasonNum,
                                        episodeNumber = epNum,
                                        title = title,
                                        streamUrl = streamUrl,
                                        description = desc,
                                        duration = duration
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                throw NetworkError.InvalidResponse
            }
            episodes.sortedBy { it.episodeNumber }
        }
    }
}
