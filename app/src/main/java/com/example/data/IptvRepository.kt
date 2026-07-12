package com.example.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import com.example.config.ProviderConfigRegistry

class IptvRepository(
    private val dao: IptvDao, 
    private val context: android.content.Context,
    private val xtreamApiClient: com.example.core.network.XtreamApiClient = com.example.core.network.XtreamApiClient(com.example.core.network.NetworkClientFactory.sharedClient)
) {

    var testFootballApiClient: FootballApiClient? = null
    var footballScheduleTimeoutMs: Long = 25_000L

    fun clearCache() {
        // No-op now that we utilize Room SQLite database cache
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
            val response = xtreamApiClient.makeHttpGetRequest(urlString)
            if (response == null) {
                return@withContext Result.failure(Exception("Unable to connect to Xtream Codes server. Please check the URL and connection."))
            }
            try {
                val root = org.json.JSONObject(response!!)
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
        if (isDemo) {
            val epgEntities = mutableListOf<EpgProgramEntity>()
            IptvMockData.LiveChannels.take(50).forEach { channel ->
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
            
            // Seed content cache for demo session
            syncAllProviderContent()
        } else {
            try {
                val format = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
                    .getString("stream_format", "TS") ?: "TS"
                val channels = xtreamApiClient.fetchXtreamLiveChannels(session, null, format)
                val realEpg = XmltvEpgParser.fetchAndParseXtreamXmltv(session, channels)
                dao.insertEpgPrograms(realEpg)
                
                // Pre-seed core content cache on login
                syncCategories("LIVE")
                syncCategories("MOVIE")
                syncCategories("SERIES")
                
                val entities = channels.mapIndexed { index, channel -> channel.toEntity(sortOrder = index, updatedAt = System.currentTimeMillis()) }
                dao.clearLiveChannels(null)
                dao.upsertLiveChannels(entities)
            } catch (e: Exception) {
                android.util.Log.e("IptvRepository", "EPG fetch/parse/preseed failed for current session.", e)
            }
        }

        return@withContext Result.success(session)
    }

    suspend fun logout() {
        dao.clearSession()
        clearCache()
    }

    // --- Core Content Fetching (Categories, Live, Movies, Series) ---

    suspend fun getCachedCategories(providerId: String): List<Category> = withContext(Dispatchers.IO) {
        val cached = dao.observeCategories("live").firstOrNull() ?: emptyList()
        cached.map { it.toDomain() }
    }

    fun getCategories(type: String): Flow<List<Category>> = flow {
        // First look in Room cache
        val cached = dao.observeCategories(type).firstOrNull() ?: emptyList()
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toDomain() })
        }

        val maxUpdated = dao.getMaxCategoryUpdatedAt(type) ?: 0L
        val isStale = (System.currentTimeMillis() - maxUpdated) > (24 * 3600 * 1000) // 24 hours stale

        if (cached.isEmpty() || isStale) {
            val session = dao.getSessionDirect()
            if (session != null && !isDemoSession(session)) {
                try {
                    val remote = xtreamApiClient.fetchXtreamCategories(session, type)
                    if (remote.isNotEmpty()) {
                        val entities = remote.mapIndexed { index, cat -> cat.toEntity(sortOrder = index, updatedAt = System.currentTimeMillis()) }
                        dao.clearCategoriesByType(type)
                        dao.upsertCategories(entities)
                        
                        val newCached = dao.observeCategories(type).firstOrNull() ?: emptyList()
                        emit(newCached.map { it.toDomain() })
                        return@flow
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IptvRepository", "Failed to sync remote categories", e)
                }
            } else {
                // Demo / mock setup
                val demo = IptvMockData.Categories.filter { it.type == type }
                val entities = demo.mapIndexed { index, cat -> cat.toEntity(sortOrder = index, updatedAt = System.currentTimeMillis()) }
                dao.clearCategoriesByType(type)
                dao.upsertCategories(entities)
                emit(demo)
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    fun observeVisibleCategories(type: String): Flow<List<Category>> {
        return dao.observeVisibleCategories(type).map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    fun observeAllCategoriesForManagement(type: String): Flow<List<CategoryManagementItem>> {
        return dao.observeAllCategoriesForManagement(type).map { entities ->
            entities.map { it.toManagementItem() }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun setCategoryHidden(type: String, categoryId: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        dao.setCategoryHidden(type, categoryId, hidden)
    }

    suspend fun setCategoryPinned(type: String, categoryId: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        dao.setCategoryPinned(type, categoryId, pinned)
    }

    suspend fun updateCategorySortOrder(type: String, orderedCategoryIds: List<String>) = withContext(Dispatchers.IO) {
        orderedCategoryIds.forEachIndexed { index, categoryId ->
            dao.updateCategorySortOrderSingle(type, categoryId, index)
        }
    }

    suspend fun resetCategoryCustomization(type: String) = withContext(Dispatchers.IO) {
        dao.clearCategoriesByType(type)
        syncCategories(type)
    }

    fun getLiveChannels(categoryId: String? = null): Flow<List<LiveChannel>> = flow {
        val cleanCategoryId = if (categoryId.isNullOrBlank()) null else categoryId
        
        // Load from database cache
        val cached = dao.observeLiveChannels(cleanCategoryId).firstOrNull() ?: emptyList()
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toDomain() })
        }

        val maxUpdated = if (cleanCategoryId != null) {
            dao.getMaxLiveChannelUpdatedAt(cleanCategoryId)
        } else {
            dao.getMaxLiveChannelUpdatedAtGlobal()
        } ?: 0L
        val isStale = (System.currentTimeMillis() - maxUpdated) > (12 * 3600 * 1000) // 12 hours stale

        if (cached.isEmpty() || isStale) {
            val session = dao.getSessionDirect()
            if (session != null && !isDemoSession(session)) {
                try {
                    val format = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
                        .getString("stream_format", "TS") ?: "TS"
                    val remote = xtreamApiClient.fetchXtreamLiveChannels(session, cleanCategoryId, format)
                    if (remote.isNotEmpty()) {
                        val entities = remote.mapIndexed { index, channel -> channel.toEntity(sortOrder = index, updatedAt = System.currentTimeMillis()) }
                        dao.clearLiveChannels(cleanCategoryId)
                        dao.upsertLiveChannels(entities)
                        
                        val newCached = dao.observeLiveChannels(cleanCategoryId).firstOrNull() ?: emptyList()
                        emit(newCached.map { it.toDomain() })
                        return@flow
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IptvRepository", "Failed to sync remote live channels", e)
                }
            } else {
                // Demo setup
                val demo = if (cleanCategoryId == null) {
                    IptvMockData.LiveChannels
                } else {
                    IptvMockData.LiveChannels.filter { it.categoryId == cleanCategoryId }
                }
                val entities = demo.mapIndexed { index, channel -> channel.toEntity(sortOrder = index, updatedAt = System.currentTimeMillis()) }
                dao.clearLiveChannels(cleanCategoryId)
                dao.upsertLiveChannels(entities)
                emit(demo)
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    fun getMovies(categoryId: String? = null): Flow<List<Movie>> = flow {
        val cleanCategoryId = if (categoryId.isNullOrBlank()) null else categoryId

        // Load cached movies with paged limits internally to avoid memory pressure (VOD limit 5000 items)
        val cached = dao.observeMovies(cleanCategoryId, limit = 5000, offset = 0).firstOrNull() ?: emptyList()
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toDomain() })
        }

        val maxUpdated = if (cleanCategoryId != null) {
            dao.getMaxMovieUpdatedAt(cleanCategoryId)
        } else {
            0L
        } ?: 0L
        val isStale = (System.currentTimeMillis() - maxUpdated) > (24 * 3600 * 1000) // 24 hours stale

        if (cached.isEmpty() || isStale) {
            val session = dao.getSessionDirect()
            if (session != null && !isDemoSession(session)) {
                try {
                    val remote = xtreamApiClient.fetchXtreamMovies(session, cleanCategoryId)
                    if (remote.isNotEmpty()) {
                        val entities = remote.map { movie ->
                            movie.toEntity(
                                normalizedTitle = normalizeTitle(movie.title),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        dao.clearMovies(cleanCategoryId)
                        dao.upsertMovies(entities)

                        val newCached = dao.observeMovies(cleanCategoryId, limit = 5000, offset = 0).firstOrNull() ?: emptyList()
                        emit(newCached.map { it.toDomain() })
                        return@flow
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IptvRepository", "Failed to sync remote movies", e)
                }
            } else {
                // Demo setup
                val demo = if (cleanCategoryId == null) {
                    IptvMockData.Movies
                } else {
                    IptvMockData.Movies.filter { it.categoryId == cleanCategoryId }
                }
                val entities = demo.map { movie ->
                    movie.toEntity(
                        normalizedTitle = normalizeTitle(movie.title),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                dao.clearMovies(cleanCategoryId)
                dao.upsertMovies(entities)
                emit(demo)
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    fun getSeries(categoryId: String? = null): Flow<List<Series>> = flow {
        val cleanCategoryId = if (categoryId.isNullOrBlank()) null else categoryId

        // Load cached series from DB
        val cached = dao.observeSeries(cleanCategoryId, limit = 5000, offset = 0).firstOrNull() ?: emptyList()
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toDomain() })
        }

        val maxUpdated = if (cleanCategoryId != null) {
            dao.getMaxSeriesUpdatedAt(cleanCategoryId)
        } else {
            0L
        } ?: 0L
        val isStale = (System.currentTimeMillis() - maxUpdated) > (24 * 3600 * 1000) // 24 hours stale

        if (cached.isEmpty() || isStale) {
            val session = dao.getSessionDirect()
            if (session != null && !isDemoSession(session)) {
                try {
                    val remote = xtreamApiClient.fetchXtreamSeries(session, cleanCategoryId)
                    if (remote.isNotEmpty()) {
                        val entities = remote.map { series ->
                            series.toEntity(
                                normalizedTitle = normalizeTitle(series.title),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        dao.clearSeries(cleanCategoryId)
                        dao.upsertSeries(entities)

                        val newCached = dao.observeSeries(cleanCategoryId, limit = 5000, offset = 0).firstOrNull() ?: emptyList()
                        emit(newCached.map { it.toDomain() })
                        return@flow
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IptvRepository", "Failed to sync remote series", e)
                }
            } else {
                // Demo setup
                val demo = if (cleanCategoryId == null) {
                    IptvMockData.SeriesList
                } else {
                    IptvMockData.SeriesList.filter { it.categoryId == cleanCategoryId }
                }
                val entities = demo.map { series ->
                    series.toEntity(
                        normalizedTitle = normalizeTitle(series.title),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                dao.clearSeries(cleanCategoryId)
                dao.upsertSeries(entities)
                emit(demo)
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    fun getSeasons(seriesId: String): Flow<List<Season>> = flow {
        // Load from DB cache
        val cached = dao.observeSeasons(seriesId).firstOrNull() ?: emptyList()
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toDomain() })
        }

        val maxUpdated = dao.getMaxSeasonUpdatedAt(seriesId) ?: 0L
        val isStale = (System.currentTimeMillis() - maxUpdated) > (7 * 24 * 3600 * 1000) // 7 days stale

        if (cached.isEmpty() || isStale) {
            val session = dao.getSessionDirect()
            if (session != null && !isDemoSession(session)) {
                try {
                    val remoteSeasons = xtreamApiClient.fetchXtreamSeasons(session, seriesId)
                    if (remoteSeasons.isNotEmpty()) {
                        val seasonEntities = remoteSeasons.map { it.toEntity(updatedAt = System.currentTimeMillis()) }
                        dao.clearSeasons(seriesId)
                        dao.upsertSeasons(seasonEntities)

                        // Fetch and cache episodes for this series automatically as well!
                        val allEpisodes = mutableListOf<Episode>()
                        remoteSeasons.forEach { season ->
                            val remoteEpisodes = xtreamApiClient.fetchXtreamEpisodes(session, seriesId, season.id)
                            allEpisodes.addAll(remoteEpisodes)
                        }
                        if (allEpisodes.isNotEmpty()) {
                            val episodeEntities = allEpisodes.map { ep ->
                                ep.toEntity(
                                    normalizedTitle = normalizeTitle(ep.title),
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                            dao.clearEpisodes(seriesId)
                            dao.upsertEpisodes(episodeEntities)
                        }

                        val newCached = dao.observeSeasons(seriesId).firstOrNull() ?: emptyList()
                        emit(newCached.map { it.toDomain() })
                        return@flow
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IptvRepository", "Failed to sync remote seasons/episodes", e)
                }
            } else {
                // Demo setup
                val demoSeasons = IptvMockData.Seasons.filter { it.seriesId == seriesId }
                val seasons = if (demoSeasons.isEmpty()) {
                    listOf(Season(id = "${seriesId}_s1", seriesId = seriesId, seasonNumber = 1, title = "Season 1"))
                } else {
                    demoSeasons
                }
                val seasonEntities = seasons.map { it.toEntity(updatedAt = System.currentTimeMillis()) }
                dao.clearSeasons(seriesId)
                dao.upsertSeasons(seasonEntities)

                val allDemoEpisodes = mutableListOf<Episode>()
                seasons.forEach { season ->
                    val demoEpisodes = IptvMockData.Episodes.filter { it.seriesId == seriesId && it.seasonId == season.id }
                    val eps = if (demoEpisodes.isEmpty()) {
                        listOf(
                            Episode(
                                id = "${seriesId}_e1",
                                seriesId = seriesId,
                                seasonId = season.id,
                                seasonNumber = season.seasonNumber,
                                episodeNumber = 1,
                                title = "Episode 1: Pilot",
                                streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                description = "Introduction to the trending series.",
                                duration = "45m"
                            ),
                            Episode(
                                id = "${seriesId}_e2",
                                seriesId = seriesId,
                                seasonId = season.id,
                                seasonNumber = season.seasonNumber,
                                episodeNumber = 2,
                                title = "Episode 2: The Rising",
                                streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                                description = "The journey continues with unexpected turns.",
                                duration = "45m"
                            )
                        )
                    } else {
                        demoEpisodes
                    }
                    allDemoEpisodes.addAll(eps)
                }
                val epEntities = allDemoEpisodes.map { ep ->
                    ep.toEntity(
                        normalizedTitle = normalizeTitle(ep.title),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                dao.clearEpisodes(seriesId)
                dao.upsertEpisodes(epEntities)

                emit(seasons)
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    fun getEpisodes(seriesId: String, seasonId: String): Flow<List<Episode>> = flow {
        val seasonNum = seasonId.substringAfterLast("_s").toIntOrNull() ?: 1

        // Check DB Cache
        val cached = dao.observeEpisodes(seriesId, seasonNum).firstOrNull() ?: emptyList()
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toDomain() })
        }

        val maxUpdated = dao.getMaxEpisodeUpdatedAt(seriesId) ?: 0L
        val isStale = (System.currentTimeMillis() - maxUpdated) > (7 * 24 * 3600 * 1000) // 7 days stale

        if (cached.isEmpty() || isStale) {
            val session = dao.getSessionDirect()
            if (session != null && !isDemoSession(session)) {
                try {
                    val remote = xtreamApiClient.fetchXtreamEpisodes(session, seriesId, seasonId)
                    if (remote.isNotEmpty()) {
                        val entities = remote.map { ep ->
                            ep.toEntity(
                                normalizedTitle = normalizeTitle(ep.title),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        dao.upsertEpisodes(entities)

                        val newCached = dao.observeEpisodes(seriesId, seasonNum).firstOrNull() ?: emptyList()
                        emit(newCached.map { it.toDomain() })
                        return@flow
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IptvRepository", "Failed to sync remote episodes", e)
                }
            } else {
                // Demo
                val demoEpisodes = IptvMockData.Episodes.filter { it.seriesId == seriesId && it.seasonId == seasonId }
                val eps = if (demoEpisodes.isEmpty()) {
                    listOf(
                        Episode(
                            id = "${seriesId}_e1",
                            seriesId = seriesId,
                            seasonId = seasonId,
                            seasonNumber = seasonNum,
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
                            seasonNumber = seasonNum,
                            episodeNumber = 2,
                            title = "Episode 2: The Rising",
                            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                            description = "The journey continues with unexpected turns.",
                            duration = "45m"
                        )
                    )
                } else {
                    demoEpisodes
                }
                val entities = eps.map { ep ->
                    ep.toEntity(
                        normalizedTitle = normalizeTitle(ep.title),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                dao.upsertEpisodes(entities)
                emit(eps)
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    // --- Cache Sync Controls ---

    suspend fun syncCategories(type: String) = withContext(Dispatchers.IO) {
        val session = dao.getSessionDirect() ?: return@withContext
        if (!isDemoSession(session)) {
            val remote = xtreamApiClient.fetchXtreamCategories(session, type)
            if (remote.isNotEmpty()) {
                val entities = remote.mapIndexed { index, cat -> cat.toEntity(sortOrder = index, updatedAt = System.currentTimeMillis()) }
                dao.clearCategoriesByType(type)
                dao.upsertCategories(entities)
            }
        }
    }

    suspend fun syncLiveChannels(categoryId: String? = null) = withContext(Dispatchers.IO) {
        val session = dao.getSessionDirect() ?: return@withContext
        if (!isDemoSession(session)) {
            val format = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
                .getString("stream_format", "TS") ?: "TS"
            val remote = xtreamApiClient.fetchXtreamLiveChannels(session, categoryId, format)
            if (remote.isNotEmpty()) {
                val entities = remote.mapIndexed { index, channel -> channel.toEntity(sortOrder = index, updatedAt = System.currentTimeMillis()) }
                dao.clearLiveChannels(categoryId)
                dao.upsertLiveChannels(entities)
            }
        }
    }

    suspend fun syncMovies(categoryId: String? = null) = withContext(Dispatchers.IO) {
        val session = dao.getSessionDirect() ?: return@withContext
        if (!isDemoSession(session)) {
            val remote = xtreamApiClient.fetchXtreamMovies(session, categoryId)
            if (remote.isNotEmpty()) {
                val entities = remote.map { movie ->
                    movie.toEntity(
                        normalizedTitle = normalizeTitle(movie.title),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                dao.clearMovies(categoryId)
                dao.upsertMovies(entities)
            }
        }
    }

    suspend fun syncSeries(categoryId: String? = null) = withContext(Dispatchers.IO) {
        val session = dao.getSessionDirect() ?: return@withContext
        if (!isDemoSession(session)) {
            val remote = xtreamApiClient.fetchXtreamSeries(session, categoryId)
            if (remote.isNotEmpty()) {
                val entities = remote.map { series ->
                    series.toEntity(
                        normalizedTitle = normalizeTitle(series.title),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                dao.clearSeries(categoryId)
                dao.upsertSeries(entities)
            }
        }
    }

    suspend fun syncAllProviderContent() = withContext(Dispatchers.IO) {
        coroutineScope {
            val jobs = listOf(
                async { syncCategories("LIVE") },
                async { syncCategories("MOVIE") },
                async { syncCategories("SERIES") },
                async { syncLiveChannels(null) }
            )
            jobs.awaitAll()
        }
    }

    suspend fun refreshProviderCache(force: Boolean) = withContext(Dispatchers.IO) {
        if (force) {
            dao.clearCategoriesByType("LIVE")
            dao.clearCategoriesByType("MOVIE")
            dao.clearCategoriesByType("SERIES")
            dao.clearLiveChannels(null)
            dao.clearMovies(null)
            dao.clearSeries(null)
        }
        syncAllProviderContent()
    }

    // --- Search Module (Global search using Room SQL + LIMIT) ---

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
        val cleanQuery = "%${query.trim().lowercase()}%"

        val live = if (liveEnabled) {
            dao.searchLiveChannels(cleanQuery, 30).map { it.toDomain() }
        } else emptyList()

        val movies = if (moviesEnabled) {
            dao.searchMovies(cleanQuery, 30).map { it.toDomain() }
        } else emptyList()

        val series = if (seriesEnabled) {
            dao.searchSeries(cleanQuery, 30).map { it.toDomain() }
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
        val session = dao.getSessionDirect() ?: return@withContext
        val now = System.currentTimeMillis()
        
        if (isDemoSession(session)) {
            val epgEntities = mutableListOf<EpgProgramEntity>()
            IptvMockData.LiveChannels.take(100).forEach { channel ->
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
        } else {
            try {
                val format = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
                    .getString("stream_format", "TS") ?: "TS"
                val channels = xtreamApiClient.fetchXtreamLiveChannels(session, null, format)
                val realEpg = XmltvEpgParser.fetchAndParseXtreamXmltv(session, channels)
                if (realEpg.isNotEmpty()) {
                    dao.insertEpgPrograms(realEpg)
                    dao.pruneOldEpg(now)
                } else {
                    android.util.Log.e("IptvRepository", "EPG refresh returned empty programs for current session.")
                }
            } catch (e: Exception) {
                android.util.Log.e("IptvRepository", "EPG refresh failed for current session.", e)
            }
        }
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

    suspend fun getFootballCompetitions(
        providerId: String,
        forceRefresh: Boolean = false
    ): List<FootballCompetitionPreference> = withContext(Dispatchers.IO) {
        val cached = footballPrefs.getCachedCompetitions()
        val updatedAt = footballPrefs.cachedCompetitionsUpdatedAt
        val isFresh = (System.currentTimeMillis() - updatedAt) < 24 * 60 * 60 * 1000L // 24 hours

        if (cached.isNotEmpty() && isFresh && !forceRefresh) {
            android.util.Log.d("IptvRepository", "Loaded ${cached.size} cached football competitions.")
            android.util.Log.d("IptvRepository", "Football competitions cache is fresh.")
            return@withContext cached
        }

        android.util.Log.d("IptvRepository", "Refreshing football competitions from backend.")
        val profile = ProviderConfigRegistry.currentProfile
        try {
            val client = testFootballApiClient ?: FootballApiClient(profile.footballBackendBaseUrl)
            val response = withTimeoutOrNull(10000) {
                client.getFootballCompetitions(
                    providerId = providerId,
                    country = profile.footballScheduleCountry
                )
            }
            if (response != null && response.enabled == true) {
                val comps = response.competitions.orEmpty()
                if (comps.isNotEmpty()) {
                    footballPrefs.saveCachedCompetitions(comps)
                    android.util.Log.d("IptvRepository", "Football backend returned ${comps.size} competitions.")
                    return@withContext comps
                }
            }
            if (cached.isNotEmpty()) {
                android.util.Log.d("IptvRepository", "Using stale football competition cache after failure.")
                return@withContext cached
            }
            emptyList()
        } catch (e: Exception) {
            android.util.Log.e("IptvRepository", "Failed to fetch football competitions", e)
            if (cached.isNotEmpty()) {
                android.util.Log.d("IptvRepository", "Using stale football competition cache after failure.")
                return@withContext cached
            }
            emptyList()
        }
    }

    suspend fun getFootballSchedule(
        providerId: String,
        selectedCompetitionKeys:
            List<String>
    ): List<FootballWatchMatch> =
        withContext<List<FootballWatchMatch>>(Dispatchers.IO) {
            if (
                selectedCompetitionKeys
                    .isEmpty()
            ) {
                return@withContext emptyList<FootballWatchMatch>()
            }

            val profile =
                ProviderConfigRegistry
                    .currentProfile

            if (
                !profile.features
                    .footballScheduleEnabled
            ) {
                return@withContext emptyList<FootballWatchMatch>()
            }

            val traceId =
                SystemClock
                    .elapsedRealtime()

            val totalStartedAt =
                SystemClock
                    .elapsedRealtime()

            android.util.Log.d(
                "FootballTrace",
                "REPOSITORY_START id=$traceId " +
                    "keys=${selectedCompetitionKeys.size}"
            )

            try {
                withTimeout(
                    footballScheduleTimeoutMs
                ) {
                    val client =
                        testFootballApiClient
                            ?: FootballApiClient(
                                profile
                                    .footballBackendBaseUrl
                            )

                    val keysParam =
                        selectedCompetitionKeys
                            .map {
                                it.trim()
                            }
                            .filter {
                                it.isNotEmpty()
                            }
                            .distinct()
                            .joinToString(",")

                    val networkStartedAt =
                        SystemClock
                            .elapsedRealtime()

                    android.util.Log.d(
                        "FootballTrace",
                        "HTTP_START id=$traceId"
                    )

                    val response =
                        client
                            .getBeinFootballSchedule(
                                providerId =
                                    providerId,

                                country =
                                    profile
                                        .footballScheduleCountry,

                                competitionKeys =
                                    keysParam
                            )

                    android.util.Log.d(
                        "FootballTrace",
                        "HTTP_DONE id=$traceId " +
                            "matches=${response.matches.orEmpty().size} " +
                            "ms=${
                                SystemClock.elapsedRealtime() -
                                    networkStartedAt
                            }"
                    )

                    if (
                        response.enabled ==
                        false
                    ) {
                        return@withTimeout emptyList<FootballWatchMatch>()
                    }

                    val backendMatches =
                        response.matches
                            .orEmpty()

                    if (
                        backendMatches
                            .isEmpty()
                    ) {
                        return@withTimeout emptyList<FootballWatchMatch>()
                    }

                    val channelsStartedAt =
                        SystemClock
                            .elapsedRealtime()

                    val cachedLocalChannels =
                        getCachedLiveChannelsForFootballMapping()

                    android.util.Log.d(
                        "FootballTrace",
                        "CHANNELS_READY id=$traceId " +
                            "count=${cachedLocalChannels.size} " +
                            "ms=${
                                SystemClock.elapsedRealtime() -
                                    channelsStartedAt
                            }"
                    )

                    val matchingStartedAt =
                        SystemClock
                            .elapsedRealtime()

                    val groupedBeinMatches =
                        backendMatches.groupBy {
                            beinMatch ->
                            val sourceId =
                                beinMatch
                                    .sourceMatchId

                            if (
                                !sourceId
                                    .isNullOrBlank()
                            ) {
                                sourceId
                            } else {
                                val title =
                                    beinMatch
                                        .title
                                        .orEmpty()

                                val kickoff =
                                    beinMatch
                                        .kickoffUtc
                                        .orEmpty()

                                if (
                                    title.isNotBlank() &&
                                    kickoff.isNotBlank()
                                ) {
                                    val normalizedTitle =
                                        FootballMatchUtils
                                            .normalize(
                                                title
                                            )

                                    "$normalizedTitle|$kickoff"
                                } else {
                                    beinMatch
                                        .id
                                        .orEmpty()
                                }
                            }
                        }

                    val finalMatches =
                        groupedBeinMatches
                            .mapNotNull {
                                (_, groupMatches) ->

                                val resolvedVariants =
                                    groupMatches.map {
                                        backendMatch ->

                                        val localChannel =
                                            FootballChannelMatcher
                                                .findLocalBeinChannelForGuideEvent(
                                                    eventChannelName =
                                                        backendMatch
                                                            .channelName,

                                                    eventChannelNumber =
                                                        backendMatch
                                                            .channelNumber,

                                                    localChannels =
                                                        cachedLocalChannels
                                                )

                                        FootballWatchMatch(
                                            match =
                                                backendMatch
                                                    .toFootballMatchCompat(),

                                            matchedChannel =
                                                localChannel,

                                            matchedProgramName =
                                                backendMatch
                                                    .channelName,

                                            confidence =
                                                if (
                                                    localChannel !=
                                                    null
                                                ) {
                                                    "BEIN_CHANNEL_MATCHED"
                                                } else {
                                                    "CHANNEL_NOT_FOUND"
                                                }
                                        )
                                    }

                                if (
                                    resolvedVariants
                                        .isEmpty()
                                ) {
                                    null
                                } else {
                                    val mappedVariants =
                                        resolvedVariants
                                            .filter {
                                                it.matchedChannel !=
                                                    null
                                            }

                                    if (
                                        mappedVariants
                                            .isNotEmpty()
                                    ) {
                                        mappedVariants
                                            .sortedBy {
                                                it.matchedProgramName
                                                    .orEmpty()
                                            }
                                            .first()
                                    } else {
                                        resolvedVariants
                                            .sortedBy {
                                                it.matchedProgramName
                                                    .orEmpty()
                                            }
                                            .first()
                                    }
                                }
                            }

                    android.util.Log.d(
                        "FootballTrace",
                        "MATCHING_DONE id=$traceId " +
                            "groups=${groupedBeinMatches.size} " +
                            "result=${finalMatches.size} " +
                            "ms=${
                                SystemClock.elapsedRealtime() -
                                    matchingStartedAt
                            }"
                    )

                    android.util.Log.d(
                        "FootballTrace",
                        "REPOSITORY_DONE id=$traceId " +
                            "totalMs=${
                                SystemClock.elapsedRealtime() -
                                    totalStartedAt
                            }"
                    )

                    finalMatches
                }
            } catch (
                e: TimeoutCancellationException
            ) {
                android.util.Log.e(
                    "FootballTrace",
                    "REPOSITORY_TIMEOUT id=$traceId " +
                        "totalMs=${
                            SystemClock.elapsedRealtime() -
                                totalStartedAt
                        }",
                    e
                )

                throw e
            } catch (
                e: CancellationException
            ) {
                android.util.Log.d(
                    "FootballTrace",
                    "REPOSITORY_CANCELLED id=$traceId"
                )

                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "FootballTrace",
                    "REPOSITORY_ERROR id=$traceId",
                    e
                )

                throw e
            }
        }

    suspend fun getCachedLiveChannelsForFootballMapping():
        List<LiveChannel> =
        withContext(Dispatchers.IO) {
            val startedAt =
                SystemClock.elapsedRealtime()

            android.util.Log.d(
                "FootballTrace",
                "CHANNEL_QUERY_START"
            )

            try {
                val entities =
                    dao.getCachedBeinChannelsSnapshot()

                val result =
                    entities.map {
                        it.toDomain()
                    }

                android.util.Log.d(
                    "FootballTrace",
                    "CHANNEL_QUERY_DONE " +
                        "count=${result.size} " +
                        "ms=${SystemClock.elapsedRealtime() - startedAt}"
                )

                result
            } catch (
                e: CancellationException
            ) {
                android.util.Log.d(
                    "FootballTrace",
                    "CHANNEL_QUERY_CANCELLED"
                )

                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "FootballTrace",
                    "CHANNEL_QUERY_ERROR",
                    e
                )

                emptyList()
            }
        }

    suspend fun loadHome(providerId: String): Result<HomeResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = com.example.config.ProviderConfigRegistry.currentProfile.backendBaseUrl
            val redactedUrl = com.example.core.redaction.SensitiveDataRedactor.redactUrl("$baseUrl/api/v1/home?provider_id=$providerId")
            android.util.Log.d("IptvRepository", "Loading home from: $redactedUrl")
            val client = HomeApiClient(baseUrl)
            val response = client.getHome(providerId)
            Result.success(response)
        } catch (e: Exception) {
            val redactedMsg = com.example.core.redaction.SensitiveDataRedactor.redactExceptionMessage(e.message)
            android.util.Log.e("IptvRepository", "Failed to load home for providerId $providerId: $redactedMsg")
            Result.failure(e)
        }
    }

    private fun normalizeTitle(title: String): String {
        val temp = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
        var clean = temp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        
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

    fun normalizeTitleForSearch(title: String): String {
        return normalizeTitle(title)
    }

    suspend fun findMatchingMovie(title: String): Movie? = withContext(Dispatchers.IO) {
        try {
            val normalizedSearch = normalizeTitleForSearch(title)
            val alphaSearch = toAlphanumeric(title)
            if (normalizedSearch.isEmpty() && alphaSearch.isEmpty()) return@withContext null

            // 1. Try exact normalized match first from database
            val exactMatch = dao.findBestMovieMatch(normalizedSearch)
            if (exactMatch != null) {
                return@withContext exactMatch.toDomain()
            }

            // 2. Fallback to a SQLite LIKE query with limit 20
            val likeQuery = "%$normalizedSearch%"
            val candidates = dao.findMovieMatchesLike(likeQuery, 20)
            if (candidates.isEmpty()) return@withContext null

            // 3. Score candidates in Kotlin
            var bestMatch: MovieStreamEntity? = null
            var bestScore = 0

            for (candidate in candidates) {
                val candidateNormalized = candidate.normalizedTitle
                val candidateAlpha = toAlphanumeric(candidate.title)

                var score = 0
                if (alphaSearch == candidateAlpha) {
                    score = 10
                } else if (normalizedSearch == candidateNormalized) {
                    score = 9
                } else if (candidateAlpha.contains(alphaSearch) || alphaSearch.contains(candidateAlpha)) {
                    score = 8
                } else if (candidateNormalized.contains(normalizedSearch) || normalizedSearch.contains(candidateNormalized)) {
                    score = 7
                } else {
                    // Word overlap
                    val searchWords = normalizedSearch.split(" ").filter { it.length > 2 && it !in listOf("the", "and", "for", "with") }
                    if (searchWords.isNotEmpty()) {
                        val matchingWordsCount = searchWords.count { candidateNormalized.contains(it) }
                        if (matchingWordsCount == searchWords.size) {
                            score = 6
                        } else if (matchingWordsCount >= (searchWords.size + 1) / 2) {
                            score = 5
                        }
                    }
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = candidate
                    if (bestScore == 10) break
                }
            }

            // 4. Require strict minimum score of 7
            if (bestScore >= 7 && bestMatch != null) {
                bestMatch.toDomain()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("IptvRepository", "findMatchingMovie failed", e)
            null
        }
    }

    suspend fun findMatchingSeries(title: String): Series? = withContext(Dispatchers.IO) {
        try {
            val normalizedSearch = normalizeTitleForSearch(title)
            val alphaSearch = toAlphanumeric(title)
            if (normalizedSearch.isEmpty() && alphaSearch.isEmpty()) return@withContext null

            // 1. Try exact normalized match first from database
            val exactMatch = dao.findBestSeriesMatch(normalizedSearch)
            if (exactMatch != null) {
                return@withContext exactMatch.toDomain()
            }

            // 2. Fallback to a SQLite LIKE query with limit 20
            val likeQuery = "%$normalizedSearch%"
            val candidates = dao.findSeriesMatchesLike(likeQuery, 20)
            if (candidates.isEmpty()) return@withContext null

            // 3. Score candidates in Kotlin
            var bestMatch: SeriesStreamEntity? = null
            var bestScore = 0

            for (candidate in candidates) {
                val candidateNormalized = candidate.normalizedTitle
                val candidateAlpha = toAlphanumeric(candidate.title)

                var score = 0
                if (alphaSearch == candidateAlpha) {
                    score = 10
                } else if (normalizedSearch == candidateNormalized) {
                    score = 9
                } else if (candidateAlpha.contains(alphaSearch) || alphaSearch.contains(candidateAlpha)) {
                    score = 8
                } else if (candidateNormalized.contains(normalizedSearch) || normalizedSearch.contains(candidateNormalized)) {
                    score = 7
                } else {
                    // Word overlap
                    val searchWords = normalizedSearch.split(" ").filter { it.length > 2 && it !in listOf("the", "and", "for", "with") }
                    if (searchWords.isNotEmpty()) {
                        val matchingWordsCount = searchWords.count { candidateNormalized.contains(it) }
                        if (matchingWordsCount == searchWords.size) {
                            score = 6
                        } else if (matchingWordsCount >= (searchWords.size + 1) / 2) {
                            score = 5
                        }
                    }
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = candidate
                    if (bestScore == 10) break
                }
            }

            // 4. Require strict minimum score of 7
            if (bestScore >= 7 && bestMatch != null) {
                bestMatch.toDomain()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("IptvRepository", "findMatchingSeries failed", e)
            null
        }
    }
}

data class SearchResults(
    val liveChannels: List<LiveChannel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList()
)
