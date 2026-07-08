package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class IptvRepository(private val dao: IptvDao, private val context: android.content.Context) {

    private var cachedAllMovies: List<Movie>? = null
    private var cachedAllSeries: List<Series>? = null

    fun clearCache() {
        cachedAllMovies = null
        cachedAllSeries = null
    }

    private fun isDemoSession(session: SessionEntity): Boolean {
        return session.username == "demo_user" || session.username == "demo" || session.serverUrl.contains("demo") || session.serverUrl.isBlank()
    }

    // --- Session / Authentication ---

    val activeSession: Flow<SessionEntity?> = dao.getSessionFlow()

    suspend fun login(username: String, token: String, serverUrl: String): Result<SessionEntity> = withContext(Dispatchers.IO) {
        clearCache()
        if (username.isBlank() || token.isBlank() || serverUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("All login fields must be filled"))
        }
        val cleanServerUrl = serverUrl.trim().removeSuffix("/")
        if (!cleanServerUrl.startsWith("http://") && !cleanServerUrl.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid Server URL format. Must start with http:// or https://"))
        }

        val isDemo = username == "demo_user" || username == "demo" || cleanServerUrl.contains("demo")
        var expiryDate = "2028-12-31 (Active)"
        var status = "Active"
        var maxConnections = 4
        var activeConnections = 1

        if (!isDemo) {
            // Real Xtream Codes validation!
            val urlString = "$cleanServerUrl/player_api.php?username=$username&password=$token"
            val response = IptvMockData.makeHttpGetRequest(urlString)
            if (response == null) {
                return@withContext Result.failure(Exception("Unable to connect to Xtream Codes server. Please check the URL and connection."))
            }
            try {
                val root = org.json.JSONObject(response)
                val userInfo = root.optJSONObject("user_info")
                if (userInfo == null) {
                    val auth = root.optInt("auth", -1)
                    if (auth == 0) {
                        return@withContext Result.failure(Exception("Invalid username or password."))
                    }
                    return@withContext Result.failure(Exception("Invalid response from server. Not a valid Xtream Codes server."))
                }
                status = userInfo.optString("status", "Active")
                if (status != "Active" && status != "active" && status.isNotEmpty()) {
                    return@withContext Result.failure(Exception("Account is not active (Status: $status)"))
                }
                val exp = userInfo.optLong("exp_date", 0L)
                expiryDate = if (exp == 0L) {
                    "Unlimited"
                } else {
                    val date = java.util.Date(exp * 1000L)
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    format.format(date)
                }
                maxConnections = userInfo.optInt("max_connections", 1)
                activeConnections = userInfo.optInt("active_cons", 0)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Authentication parser error: ${e.message}"))
            }
        }

        val session = SessionEntity(
            username = username,
            token = token,
            serverUrl = cleanServerUrl,
            expiryDate = expiryDate,
            status = status,
            maxConnections = maxConnections,
            activeConnections = activeConnections
        )
        dao.insertSession(session)
        
        // When logged in, pre-seed EPG cache automatically
        val now = System.currentTimeMillis()
        val epgEntities = mutableListOf<EpgProgramEntity>()
        val channels = if (!isDemo) {
            try {
                IptvMockData.fetchXtreamLiveChannels(session, null)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            IptvMockData.LiveChannels
        }

        channels.take(50).forEach { channel ->
            val programs = IptvMockData.getEpgForChannel(channel.id, now)
            epgEntities.addAll(programs.map {
                EpgProgramEntity(
                    channelId = it.channelId,
                    title = it.title,
                    description = it.description,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    epgId = it.id
                )
            })
        }
        dao.insertEpgPrograms(epgEntities)

        return@withContext Result.success(session)
    }

    suspend fun logout() {
        dao.clearSession()
        clearCache()
    }

    // --- Core Content Fetching (Categories, Live, Movies, Series) ---

    fun getCategories(type: String): Flow<List<Category>> = flow {
        val session = dao.getSessionDirect()
        if (session != null && !isDemoSession(session)) {
            try {
                val categories = IptvMockData.fetchXtreamCategories(session, type)
                if (categories.isNotEmpty()) {
                    emit(categories)
                    return@flow
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        emit(IptvMockData.Categories.filter { it.type == type })
    }.flowOn(Dispatchers.IO)

    fun getLiveChannels(categoryId: String? = null): Flow<List<LiveChannel>> = flow {
        val session = dao.getSessionDirect()
        if (session != null && !isDemoSession(session)) {
            try {
                val format = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
                    .getString("stream_format", "TS") ?: "TS"
                val channels = IptvMockData.fetchXtreamLiveChannels(session, categoryId, format)
                if (channels.isNotEmpty()) {
                    emit(channels)
                    return@flow
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val channels = if (categoryId == null) {
            IptvMockData.LiveChannels
        } else {
            IptvMockData.LiveChannels.filter { it.categoryId == categoryId }
        }
        emit(channels)
    }.flowOn(Dispatchers.IO)

    fun getMovies(categoryId: String? = null): Flow<List<Movie>> = flow {
        val session = dao.getSessionDirect()
        if (session != null && !isDemoSession(session)) {
            try {
                val movies = IptvMockData.fetchXtreamMovies(session, categoryId)
                if (movies.isNotEmpty()) {
                    emit(movies)
                    return@flow
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val movies = if (categoryId == null) {
            IptvMockData.Movies
        } else {
            IptvMockData.Movies.filter { it.categoryId == categoryId }
        }
        emit(movies)
    }.flowOn(Dispatchers.IO)

    fun getSeries(categoryId: String? = null): Flow<List<Series>> = flow {
        val session = dao.getSessionDirect()
        if (session != null && !isDemoSession(session)) {
            try {
                val series = IptvMockData.fetchXtreamSeries(session, categoryId)
                if (series.isNotEmpty()) {
                    emit(series)
                    return@flow
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val series = if (categoryId == null) {
            IptvMockData.SeriesList
        } else {
            IptvMockData.SeriesList.filter { it.categoryId == categoryId }
        }
        emit(series)
    }.flowOn(Dispatchers.IO)

    fun getSeasons(seriesId: String): Flow<List<Season>> = flow {
        val session = dao.getSessionDirect()
        if (session != null && !isDemoSession(session)) {
            try {
                val seasons = IptvMockData.fetchXtreamSeasons(session, seriesId)
                if (seasons.isNotEmpty()) {
                    emit(seasons)
                    return@flow
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val localSeasons = IptvMockData.Seasons.filter { it.seriesId == seriesId }
        if (localSeasons.isEmpty()) {
            emit(listOf(
                Season(id = "${seriesId}_s1", seriesId = seriesId, seasonNumber = 1, title = "Season 1")
            ))
        } else {
            emit(localSeasons)
        }
    }.flowOn(Dispatchers.IO)

    fun getEpisodes(seriesId: String, seasonId: String): Flow<List<Episode>> = flow {
        val session = dao.getSessionDirect()
        if (session != null && !isDemoSession(session)) {
            try {
                val episodes = IptvMockData.fetchXtreamEpisodes(session, seriesId, seasonId)
                if (episodes.isNotEmpty()) {
                    emit(episodes)
                    return@flow
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val localEpisodes = IptvMockData.Episodes.filter { it.seriesId == seriesId && it.seasonId == seasonId }
        if (localEpisodes.isEmpty()) {
            emit(listOf(
                Episode(
                    id = "${seriesId}_e1",
                    seriesId = seriesId,
                    seasonId = seasonId,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "Episode 1: Pilot",
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    description = "Introduction to the trending series.",
                    duration = "45m"
                ),
                Episode(
                    id = "${seriesId}_e2",
                    seriesId = seriesId,
                    seasonId = seasonId,
                    seasonNumber = 1,
                    episodeNumber = 2,
                    title = "Episode 2: The Rising",
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    description = "The journey continues with unexpected turns.",
                    duration = "45m"
                )
            ))
        } else {
            emit(localEpisodes)
        }
    }.flowOn(Dispatchers.IO)

    // --- Search Module (Global search filtered by enabled features) ---

    fun searchContent(
        query: String,
        liveEnabled: Boolean,
        moviesEnabled: Boolean,
        seriesEnabled: Boolean
    ): Flow<SearchResults> = flow {
        if (query.isBlank()) {
            emit(SearchResults())
            return@flow
        }
        val cleanQuery = query.lowercase()

        val session = dao.getSessionDirect()
        if (session != null && !isDemoSession(session)) {
            try {
                val format = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
                    .getString("stream_format", "TS") ?: "TS"
                val live = if (liveEnabled) {
                    IptvMockData.fetchXtreamLiveChannels(session, null, format).filter { it.name.lowercase().contains(cleanQuery) }
                } else emptyList()

                val movies = if (moviesEnabled) {
                    IptvMockData.fetchXtreamMovies(session, null).filter { it.title.lowercase().contains(cleanQuery) }
                } else emptyList()

                val series = if (seriesEnabled) {
                    IptvMockData.fetchXtreamSeries(session, null).filter { it.title.lowercase().contains(cleanQuery) }
                } else emptyList()

                emit(SearchResults(live, movies, series))
                return@flow
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val live = if (liveEnabled) {
            IptvMockData.LiveChannels.filter { it.name.lowercase().contains(cleanQuery) }
        } else emptyList()

        val movies = if (moviesEnabled) {
            IptvMockData.Movies.filter { it.title.lowercase().contains(cleanQuery) }
        } else emptyList()

        val series = if (seriesEnabled) {
            IptvMockData.SeriesList.filter { it.title.lowercase().contains(cleanQuery) }
        } else emptyList()

        emit(SearchResults(live, movies, series))
    }.flowOn(Dispatchers.IO)

    // --- Favorites Module ---

    val favorites: Flow<List<FavoriteEntity>> = dao.getAllFavorites()

    fun isFavorite(contentId: String, type: String): Flow<Boolean> = dao.isFavorite(contentId, type)

    suspend fun addFavorite(favorite: FavoriteEntity) {
        dao.addFavorite(favorite)
    }

    suspend fun removeFavorite(contentId: String, type: String) {
        dao.removeFavorite(contentId, type)
    }

    // --- Continue Watching Module ---

    val continueWatching: Flow<List<ContinueWatchingEntity>> = dao.getContinueWatchingFlow()

    suspend fun saveContinueWatching(progress: ContinueWatchingEntity) {
        dao.saveContinueWatching(progress)
    }

    suspend fun removeContinueWatching(contentId: String) {
        dao.removeContinueWatching(contentId)
    }

    // --- Recently Watched Module ---

    val recentlyWatched: Flow<List<RecentlyWatchedEntity>> = dao.getRecentlyWatchedFlow()

    suspend fun saveRecentlyWatched(recent: RecentlyWatchedEntity) {
        dao.saveRecentlyWatched(recent)
    }

    suspend fun deleteRecent(contentId: String) {
        dao.deleteRecent(contentId)
    }

    // --- EPG Module ---

    fun getEpgForChannel(channelId: String): Flow<List<EpgProgramEntity>> {
        val now = System.currentTimeMillis()
        return dao.getEpgForChannel(channelId, now)
    }

    suspend fun getCurrentProgram(channelId: String): EpgProgramEntity? {
        val now = System.currentTimeMillis()
        return dao.getCurrentProgram(channelId, now)
    }

    suspend fun refreshEpg() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val epgEntities = mutableListOf<EpgProgramEntity>()
        val session = dao.getSessionDirect()
        val channels = if (session != null && !isDemoSession(session)) {
            try {
                val format = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
                    .getString("stream_format", "TS") ?: "TS"
                IptvMockData.fetchXtreamLiveChannels(session, null, format)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            IptvMockData.LiveChannels
        }
        
        channels.take(100).forEach { channel ->
            val programs = IptvMockData.getEpgForChannel(channel.id, now)
            epgEntities.addAll(programs.map {
                EpgProgramEntity(
                    channelId = it.channelId,
                    title = it.title,
                    description = it.description,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    epgId = it.id
                )
            })
        }
        dao.insertEpgPrograms(epgEntities)
        dao.pruneOldEpg(now)
    }

    // --- Parental Controls Module ---

    val parentalSettings: Flow<ParentalControlEntity?> = dao.getParentalSettingsFlow()

    suspend fun getParentalSettingsDirect(): ParentalControlEntity? = dao.getParentalSettingsDirect()

    suspend fun saveParentalSettings(settings: ParentalControlEntity) {
        dao.saveParentalSettings(settings)
    }

    suspend fun clearParentalSettings() {
        dao.clearParentalSettings()
    }

    // --- Football Schedule Module ---

    val footballPrefs by lazy { FootballPrefs(context) }

    suspend fun getFootballOptions(providerId: String): FootballOptionsResponse? = withContext(Dispatchers.IO) {
        try {
            val client = FootballApiClient()
            client.getFootballOptions(providerId)
        } catch (e: Exception) {
            android.util.Log.e("IptvRepository", "Failed to fetch football options", e)
            null
        }
    }

    suspend fun getFootballSchedule(
        providerId: String,
        selectedCompetitionCodes: List<String>,
        selectedTeamIds: List<Int>
    ): List<FootballWatchMatch> = withContext(Dispatchers.IO) {
        if (selectedCompetitionCodes.isEmpty() && selectedTeamIds.isEmpty()) {
            return@withContext emptyList()
        }
        try {
            val client = FootballApiClient()
            val codesParam = if (selectedCompetitionCodes.isNotEmpty()) selectedCompetitionCodes.joinToString(",") else null
            val idsParam = if (selectedTeamIds.isNotEmpty()) selectedTeamIds.joinToString(",") else null
            
            val response = client.getFootballSchedule(providerId, codesParam, idsParam)
            if (response.enabled == false) {
                return@withContext emptyList()
            }
            val matches = response.matches ?: return@withContext emptyList()
            
            // Load all available channels to match against
            val channels = try {
                getLiveChannels(null).firstOrNull() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            matches.map { match ->
                val kickoffMillis = FootballMatchUtils.parseUtcToMillis(match.kickoffUtc)
                val fromTime = kickoffMillis - (2 * 3600 * 1000) // kickoff - 2 hours
                val toTime = kickoffMillis + (3 * 3600 * 1000)   // kickoff + 3 hours
                
                val epgInWindow = if (kickoffMillis > 0) {
                    dao.getEpgProgramsInWindow(fromTime, toTime)
                } else {
                    emptyList()
                }

                FootballMatchUtils.matchMatchWithEpg(match, epgInWindow, channels)
            }
        } catch (e: Exception) {
            android.util.Log.e("IptvRepository", "Failed to fetch football schedule", e)
            emptyList()
        }
    }

    suspend fun loadHome(providerId: String): Result<HomeResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = com.example.config.ProviderConfigRegistry.currentProfile.backendBaseUrl
            android.util.Log.d("IptvRepository", "Loading home from: $baseUrl/api/v1/home?provider_id=$providerId")
            val client = HomeApiClient(baseUrl)
            val response = client.getHome(providerId)
            Result.success(response)
        } catch (e: Exception) {
            android.util.Log.e("IptvRepository", "Failed to load home for providerId $providerId", e)
            Result.failure(e)
        }
    }

    private fun normalizeTitle(title: String): String {
        var clean = title.lowercase()
        
        // Remove common year patterns like (2024), [2024], 2024
        clean = clean.replace(Regex("\\b(19|20)\\d{2}\\b"), "")
        
        // Remove specific junk strings/resolutions/quality tags
        val junkPatterns = listOf(
            "1080p", "720p", "4k", "uhd", "fhd", "hd", "sd", "3d", "hevc", "h264", "x264", "h265", "x265",
            "bluray", "web-dl", "webdl", "bdrip", "brrip", "dvdrip", "scr", "camrip", "cam",
            "dual audio", "multi-audio", "multi-subs", "multisubs", "multi", "dubbed", "subbed",
            "latino", "castellano", "español", "spanish", "english", "french", "german", "italian", "ita", "eng",
            "aac", "dts", "dd5.1", "ac3", "atmos"
        )
        for (pattern in junkPatterns) {
            clean = clean.replace(Regex("\\b$pattern\\b"), "")
        }
        
        // Remove punctuation and special characters, keep only letters, numbers, and spaces
        clean = clean.replace(Regex("[^a-z0-9\\s]"), "")
        
        // Collapse multiple spaces to a single space
        clean = clean.replace(Regex("\\s+"), " ")
        
        return clean.trim()
    }

    private fun toAlphanumeric(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    suspend fun findMatchingMovie(title: String): Movie? = withContext(Dispatchers.IO) {
        try {
            if (cachedAllMovies.isNullOrEmpty()) {
                val session = dao.getSessionDirect()
                if (session != null && !isDemoSession(session)) {
                    val allMoviesList = mutableListOf<Movie>()
                    // Try to fetch all movies directly first
                    val directMovies = IptvMockData.fetchXtreamMovies(session, null)
                    if (directMovies.isNotEmpty()) {
                        allMoviesList.addAll(directMovies)
                    } else {
                        // Fallback: fetch category by category in parallel
                        val categories = getCategories("MOVIE").firstOrNull() ?: emptyList()
                        coroutineScope {
                            val deferredMovies = categories.map { category ->
                                async {
                                    try {
                                        IptvMockData.fetchXtreamMovies(session, category.id)
                                    } catch (e: Exception) {
                                        emptyList<Movie>()
                                    }
                                }
                            }
                            allMoviesList.addAll(deferredMovies.awaitAll().flatten())
                        }
                    }
                    cachedAllMovies = allMoviesList.distinctBy { it.id }
                } else {
                    cachedAllMovies = IptvMockData.Movies
                }
            }
            val movies = cachedAllMovies ?: emptyList()
            if (movies.isEmpty()) return@withContext null
            
            val cleanTrending = normalizeTitle(title)
            val alphaTrending = toAlphanumeric(title)
            if (cleanTrending.isEmpty() && alphaTrending.isEmpty()) return@withContext null
            
            var bestMatch: Movie? = null
            var bestScore = 0
            
            for (movie in movies) {
                val cleanProvider = normalizeTitle(movie.title)
                val alphaProvider = toAlphanumeric(movie.title)
                if (cleanProvider.isEmpty() && alphaProvider.isEmpty()) continue
                
                var score = 0
                if (alphaTrending == alphaProvider) {
                    score = 10
                } else if (cleanTrending == cleanProvider) {
                    score = 9
                } else if (alphaProvider.contains(alphaTrending) || alphaTrending.contains(alphaProvider)) {
                    score = 8
                } else if (cleanProvider.contains(cleanTrending) || cleanTrending.contains(cleanProvider)) {
                    score = 7
                } else {
                    // Word overlap
                    val trendingWords = cleanTrending.split(" ").filter { it.length > 2 && it !in listOf("the", "and", "for", "with") }
                    if (trendingWords.isNotEmpty()) {
                        val matchingWordsCount = trendingWords.count { cleanProvider.contains(it) }
                        if (matchingWordsCount == trendingWords.size) {
                            score = 6
                        } else if (matchingWordsCount >= (trendingWords.size + 1) / 2) {
                            score = 5
                        }
                    }
                }
                
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = movie
                    if (bestScore == 10) break // Exact match, stop
                }
            }
            
            bestMatch
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun findMatchingSeries(title: String): Series? = withContext(Dispatchers.IO) {
        try {
            if (cachedAllSeries.isNullOrEmpty()) {
                val session = dao.getSessionDirect()
                if (session != null && !isDemoSession(session)) {
                    val allSeriesList = mutableListOf<Series>()
                    // Try to fetch all series directly first
                    val directSeries = IptvMockData.fetchXtreamSeries(session, null)
                    if (directSeries.isNotEmpty()) {
                        allSeriesList.addAll(directSeries)
                    } else {
                        // Fallback: fetch category by category in parallel
                        val categories = getCategories("SERIES").firstOrNull() ?: emptyList()
                        coroutineScope {
                            val deferredSeries = categories.map { category ->
                                async {
                                    try {
                                        IptvMockData.fetchXtreamSeries(session, category.id)
                                    } catch (e: Exception) {
                                        emptyList<Series>()
                                    }
                                }
                            }
                            allSeriesList.addAll(deferredSeries.awaitAll().flatten())
                        }
                    }
                    cachedAllSeries = allSeriesList.distinctBy { it.id }
                } else {
                    cachedAllSeries = IptvMockData.SeriesList
                }
            }
            val seriesList = cachedAllSeries ?: emptyList()
            if (seriesList.isEmpty()) return@withContext null
            
            val cleanTrending = normalizeTitle(title)
            val alphaTrending = toAlphanumeric(title)
            if (cleanTrending.isEmpty() && alphaTrending.isEmpty()) return@withContext null
            
            var bestMatch: Series? = null
            var bestScore = 0
            
            for (series in seriesList) {
                val cleanProvider = normalizeTitle(series.title)
                val alphaProvider = toAlphanumeric(series.title)
                if (cleanProvider.isEmpty() && alphaProvider.isEmpty()) continue
                
                var score = 0
                if (alphaTrending == alphaProvider) {
                    score = 10
                } else if (cleanTrending == cleanProvider) {
                    score = 9
                } else if (alphaProvider.contains(alphaTrending) || alphaTrending.contains(alphaProvider)) {
                    score = 8
                } else if (cleanProvider.contains(cleanTrending) || cleanTrending.contains(cleanProvider)) {
                    score = 7
                } else {
                    // Word overlap
                    val trendingWords = cleanTrending.split(" ").filter { it.length > 2 && it !in listOf("the", "and", "for", "with") }
                    if (trendingWords.isNotEmpty()) {
                        val matchingWordsCount = trendingWords.count { cleanProvider.contains(it) }
                        if (matchingWordsCount == trendingWords.size) {
                            score = 6
                        } else if (matchingWordsCount >= (trendingWords.size + 1) / 2) {
                            score = 5
                        }
                    }
                }
                
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = series
                    if (bestScore == 10) break // Exact match, stop
                }
            }
            
            bestMatch
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class SearchResults(
    val liveChannels: List<LiveChannel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList()
)
