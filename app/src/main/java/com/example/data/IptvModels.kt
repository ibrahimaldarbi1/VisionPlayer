package com.example.data

import okhttp3.OkHttpClient
import okhttp3.Request
import com.squareup.moshi.JsonReader

data class Category(
    val id: String,
    val name: String,
    val type: String // "LIVE", "MOVIE", "SERIES"
)

data class CategoryManagementItem(
    val id: String,
    val name: String,
    val type: String,
    val sortOrder: Int,
    val hidden: Boolean,
    val pinned: Boolean
)

data class LiveChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String,
    val categoryId: String,
    val categoryName: String,
    val epgId: String,
    val channelNumber: Int,
    val isLocked: Boolean = false,
    val isAdult: Boolean = false,
    val hasCatchup: Boolean = false
)

data class Movie(
    val id: String,
    val title: String,
    val streamUrl: String,
    val posterUrl: String,
    val backdropUrl: String,
    val categoryId: String,
    val categoryName: String,
    val description: String,
    val year: String = "2024",
    val duration: String = "1h 45m",
    val genre: String = "Drama",
    val rating: String = "8.2",
    val cast: String = "John Doe, Jane Smith",
    val director: String = "Alan Smithee",
    val isAdult: Boolean = false
)

data class Series(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val categoryId: String,
    val categoryName: String,
    val description: String,
    val year: String = "2024",
    val genre: String = "Sci-Fi",
    val rating: String = "8.5",
    val cast: String = "Alice Carter, Bob Vance",
    val director: String = "Directing Team",
    val isAdult: Boolean = false
)

data class Season(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val title: String
)

data class Episode(
    val id: String,
    val seriesId: String,
    val seasonId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val streamUrl: String,
    val description: String,
    val duration: String = "45m",
    val posterUrl: String = ""
)

data class EpgProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Long, // Unix timestamp in ms
    val endTime: Long // Unix timestamp in ms
)

// --- Sample / Mock Data Generator & Real Xtream Parser ---

object IptvMockData {
    val Categories = listOf(
        // Live categories
        Category("live_news", "News & Info", "LIVE"),
        Category("live_sports", "Sports Network", "LIVE"),
        Category("live_movies", "Cinema Live", "LIVE"),
        Category("live_adult", "18+ Premium Adult", "LIVE"),
        
        // Movie categories
        Category("movie_action", "Action & Thriller", "MOVIE"),
        Category("movie_scifi", "Sci-Fi & Fantasy", "MOVIE"),
        Category("movie_comedy", "Comedy Club", "MOVIE"),
        Category("movie_adult", "18+ Adult Cinema", "MOVIE"),
        
        // Series categories
        Category("series_drama", "Top Dramas", "SERIES"),
        Category("series_mystery", "Sci-Fi & Mystery", "SERIES")
    )

    val LiveChannels = listOf(
        LiveChannel(
            id = "live_1",
            name = "Sintel HD Live",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            logoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
            categoryId = "live_movies",
            categoryName = "Cinema Live",
            epgId = "epg_sintel",
            channelNumber = 101,
            hasCatchup = true
        ),
        LiveChannel(
            id = "live_2",
            name = "Big Buck News",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            logoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            categoryId = "live_news",
            categoryName = "News & Info",
            epgId = "epg_bunny",
            channelNumber = 102
        ),
        LiveChannel(
            id = "live_3",
            name = "Tears of Steel Sports",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            logoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
            categoryId = "live_sports",
            categoryName = "Sports Network",
            epgId = "epg_tears",
            channelNumber = 103,
            hasCatchup = true
        ),
        LiveChannel(
            id = "live_4",
            name = "Elephant's Dream Cinema",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            logoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
            categoryId = "live_movies",
            categoryName = "Cinema Live",
            epgId = "epg_elephant",
            channelNumber = 104
        ),
        LiveChannel(
            id = "live_adult_1",
            name = "Late Night 18+ (Explicit)",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            logoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/SubaruOutbackOnStreetAndDirt.jpg",
            categoryId = "live_adult",
            categoryName = "18+ Premium Adult",
            epgId = "epg_adult_1",
            channelNumber = 180,
            isAdult = true
        )
    )

    val Movies = listOf(
        Movie(
            id = "movie_1",
            title = "Big Buck Bunny",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            posterUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            backdropUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            categoryId = "movie_comedy",
            categoryName = "Comedy Club",
            description = "A large and lovable rabbit named Bunny takes revenge on three pesky woodland critters who bully him and destroy his forest home.",
            year = "2008",
            duration = "9m 56s",
            genre = "Animation, Comedy",
            rating = "7.8",
            cast = "Bunny, Squirrel, Gopher",
            director = "Sacha Goedegebure"
        ),
        Movie(
            id = "movie_2",
            title = "Sintel",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            posterUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
            backdropUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
            categoryId = "movie_scifi",
            categoryName = "Sci-Fi & Fantasy",
            description = "Sintel, a lonely young woman, searches for a baby dragon she befriended named Scales, who has been kidnapped by an enormous adult dragon.",
            year = "2010",
            duration = "14m 48s",
            genre = "Fantasy, Adventure",
            rating = "8.1",
            cast = "Halina Reijn, Thom Hoffman",
            director = "Colin Levy"
        ),
        Movie(
            id = "movie_3",
            title = "Tears of Steel",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            posterUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
            backdropUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
            categoryId = "movie_action",
            categoryName = "Action & Thriller",
            description = "Set in a dystopian future Amsterdam, a group of scientists attempts to save the world from destructive giant robots using advanced virtual technology.",
            year = "2012",
            duration = "12m 14s",
            genre = "Sci-Fi, Action",
            rating = "7.9",
            cast = "Derek de Lint, Sergio Hasselbaink",
            director = "Ian Hubert"
        ),
        Movie(
            id = "movie_adult_1",
            title = "Late Night Secrets 18+",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            posterUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/SubaruOutbackOnStreetAndDirt.jpg",
            backdropUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/SubaruOutbackOnStreetAndDirt.jpg",
            categoryId = "movie_adult",
            categoryName = "18+ Adult Cinema",
            description = "A spicy late-night drama reserved exclusively for mature audiences.",
            year = "2021",
            duration = "1h 32m",
            genre = "Romance, Drama",
            rating = "6.5",
            isAdult = true
        )
    )

    val SeriesList = listOf(
        Series(
            id = "series_1",
            title = "Elephant's Universe",
            posterUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
            backdropUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
            categoryId = "series_mystery",
            categoryName = "Sci-Fi & Mystery",
            description = "Welcome to a surreal dreamscape where two men, Proog and Emo, struggle to understand the strange mechanical environment they live in.",
            year = "2015",
            genre = "Sci-Fi, Mystery",
            rating = "8.4",
            cast = "Proog, Emo",
            director = "Bassam Kurdali"
        ),
        Series(
            id = "series_2",
            title = "The Firebrand Chronicles",
            posterUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
            backdropUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
            categoryId = "series_drama",
            categoryName = "Top Dramas",
            description = "An explosive action series exploring the secret training of professional firefighters dealing with massive metropolitan hazards.",
            year = "2019",
            genre = "Action, Drama",
            rating = "7.6",
            cast = "Hero Fireman, Crew Alpha",
            director = "Google Media Team"
        )
    )

    val Seasons = listOf(
        Season("season_1_1", "series_1", 1, "Season 1: Mechanical Dreams"),
        Season("season_1_2", "series_1", 2, "Season 2: Reality Awakenings"),
        Season("season_2_1", "series_2", 1, "Season 1: First Response")
    )

    val Episodes = listOf(
        // Series 1, Season 1
        Episode(
            id = "ep_1_1_1",
            seriesId = "series_1",
            seasonId = "season_1_1",
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Introduction to the Machine",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            description = "Proog shows Emo how the grand machine works, but Emo starts questioning its logic.",
            duration = "10m"
        ),
        Episode(
            id = "ep_1_1_2",
            seriesId = "series_1",
            seasonId = "season_1_1",
            seasonNumber = 1,
            episodeNumber = 2,
            title = "Deep in the Cables",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            description = "A dangerous expedition into the underground wiring of the dreamscape leads to a surprising reunion.",
            duration = "14m"
        ),
        // Series 1, Season 2
        Episode(
            id = "ep_1_2_1",
            seriesId = "series_1",
            seasonId = "season_1_2",
            seasonNumber = 2,
            episodeNumber = 1,
            title = "Escape Velocity",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            description = "With the machine collapsing, our heroes try to find a physical escape hatch into the real world.",
            duration = "12m"
        ),
        
        // Series 2, Season 1
        Episode(
            id = "ep_2_1_1",
            seriesId = "series_2",
            seasonId = "season_2_1",
            seasonNumber = 1,
            episodeNumber = 1,
            title = "The Inferno Starts",
            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            description = "A routine call turns into an active rescue as the crew battles an chemical fire.",
            duration = "15m"
        )
    )

    fun getEpgForChannel(channelId: String, timestampMs: Long): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        val oneHourMs = 3600000L
        val baseTime = timestampMs - (2 * oneHourMs) // start 2 hours ago
        
        val channelName = channelId.replace("_", " ").uppercase()
        
        for (i in 0..10) {
            val startTime = baseTime + (i * oneHourMs)
            val endTime = startTime + oneHourMs
            programs.add(
                EpgProgram(
                    id = "${channelId}_epg_$i",
                    channelId = channelId,
                    title = "EPG Segment $i: $channelName Special",
                    description = "This is program segment number $i playing on the channel. Enjoy high-quality IPTV streaming with custom EPG metadata matching.",
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }
        return programs
    }

    // --- REAL WORLD HTTP AND JSON PARSER CLIENT FOR LEGAL XTREAM CODES STREAMS ---

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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

    fun makeHttpGetRequest(urlString: String): String? {
        return try {
            val request = Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            val response = client.newCall(request).execute()
            try {
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun fetchXtreamCategories(session: SessionEntity, type: String): List<Category> {
        val action = when (type) {
            "LIVE" -> "get_live_categories"
            "MOVIE" -> "get_vod_categories"
            "SERIES" -> "get_series_categories"
            else -> return emptyList()
        }
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=$action"
        val response = makeHttpGetRequest(urlString) ?: return emptyList()
        val categories = mutableListOf<Category>()
        try {
            val jsonArray = org.json.JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("category_id")
                val name = obj.optString("category_name")
                if (id.isNotEmpty() && name.isNotEmpty()) {
                    categories.add(Category(id = id, name = name, type = type))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return categories
    }

    fun fetchXtreamLiveChannels(session: SessionEntity, categoryId: String?, streamFormat: String = "TS"): List<LiveChannel> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_live_streams" +
                if (categoryId != null) "&category_id=$categoryId" else ""
        
        val channels = mutableListOf<LiveChannel>()
        try {
            val request = Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            try {
                if (!response.isSuccessful) return emptyList()
                val body = response.body ?: return emptyList()
                
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
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return channels
    }

    fun fetchXtreamMovies(session: SessionEntity, categoryId: String?): List<Movie> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_vod_streams" +
                if (categoryId != null) "&category_id=$categoryId" else ""
        
        val movies = mutableListOf<Movie>()
        try {
            val request = Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
                
            val response = client.newCall(request).execute()
            try {
                if (!response.isSuccessful) return emptyList()
                val body = response.body ?: return emptyList()
                
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
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return movies
    }

    fun fetchXtreamSeries(session: SessionEntity, categoryId: String?): List<Series> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_series" +
                if (categoryId != null) "&category_id=$categoryId" else ""
        
        val seriesList = mutableListOf<Series>()
        try {
            val request = Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
                
            val response = client.newCall(request).execute()
            try {
                if (!response.isSuccessful) return emptyList()
                val body = response.body ?: return emptyList()
                
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
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return seriesList
    }

    fun fetchXtreamSeasons(session: SessionEntity, seriesId: String): List<Season> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_series_info&series_id=$seriesId"
        val response = makeHttpGetRequest(urlString) ?: return emptyList()
        val seasons = mutableListOf<Season>()
        try {
            val trimResponse = response.trim()
            if (trimResponse.startsWith("[")) {
                // Raw list fallback
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
                
                // 1. Try "seasons" as JSONArray
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
                    // 2. Try "seasons" as JSONObject
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
                
                // 3. Fallback: Parse seasons from "episodes" if seasons list is still empty
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
            e.printStackTrace()
        }
        return seasons.sortedBy { it.seasonNumber }
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

    fun fetchXtreamEpisodes(session: SessionEntity, seriesId: String, seasonId: String): List<Episode> {
        val urlString = "${session.serverUrl}/player_api.php?username=${session.username}&password=${session.token}&action=get_series_info&series_id=$seriesId"
        val response = makeHttpGetRequest(urlString) ?: return emptyList()
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
                
                // 1. Try "episodes" as JSONObject
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
                    } else {
                        // Sometimes the episodes object uses season number as Int or has season lists under nested structures
                        val keys = episodesObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            if (key.toIntOrNull() == seasonNum) {
                                val nestedArray = episodesObj.optJSONArray(key)
                                if (nestedArray != null) {
                                    for (i in 0 until nestedArray.length()) {
                                        val obj = nestedArray.getJSONObject(i)
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
                    }
                } else {
                    // 2. Try "episodes" as JSONArray
                    val epArray = root.optJSONArray("episodes")
                    if (epArray != null) {
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
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return episodes.sortedBy { it.episodeNumber }
    }
}
