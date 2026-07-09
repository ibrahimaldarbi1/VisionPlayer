package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String = "active_session",
    val username: String,
    val token: String,
    val serverUrl: String,
    val expiryDate: String,
    val status: String,
    val maxConnections: Int = 1,
    val activeConnections: Int = 0
)

@Entity(tableName = "favorites", primaryKeys = ["contentId", "contentType"])
data class FavoriteEntity(
    val contentId: String,
    val contentType: String, // "LIVE", "MOVIE", "SERIES"
    val title: String,
    val posterOrLogo: String,
    val streamUrl: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val contentId: String, // movie id or episode id
    val parentId: String = "", // empty for movies, seriesId for episodes
    val contentType: String, // "MOVIE", "EPISODE"
    val title: String, // movie title or episode title
    val parentTitle: String = "", // empty for movies, series title for episodes
    val posterOrLogo: String,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recently_watched")
data class RecentlyWatchedEntity(
    @PrimaryKey val contentId: String, // channel id, movie id, or series id
    val contentType: String, // "LIVE", "MOVIE", "SERIES"
    val title: String,
    val posterOrLogo: String,
    val streamUrl: String,
    val categoryName: String = "",
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "epg_programs", primaryKeys = ["channelId", "startTime"])
data class EpgProgramEntity(
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Long, // Epoch ms
    val endTime: Long, // Epoch ms
    val epgId: String = ""
)

@Entity(tableName = "parental_controls")
data class ParentalControlEntity(
    @PrimaryKey val id: String = "global_pin",
    val pin: String, // 4-digit pin
    val lockedCategories: String = "" // Comma-separated locked category names/IDs
)

@Entity(
    tableName = "categories",
    primaryKeys = ["id", "type"],
    indices = [Index(value = ["type"])]
)
data class CategoryEntity(
    val id: String,
    val name: String,
    val type: String, // LIVE, MOVIE, SERIES
    val sortOrder: Int,
    val hidden: Boolean = false,
    val pinned: Boolean = false,
    val updatedAt: Long
)

@Entity(
    tableName = "live_channels",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
        Index(value = ["epgId"])
    ]
)
data class LiveChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String,
    val categoryId: String,
    val categoryName: String,
    val epgId: String,
    val channelNumber: Int,
    val isLocked: Boolean,
    val isAdult: Boolean,
    val hasCatchup: Boolean,
    val hidden: Boolean = false,
    val sortOrder: Int,
    val updatedAt: Long
)

@Entity(
    tableName = "movie_streams",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["normalizedTitle"]),
        Index(value = ["title"])
    ]
)
data class MovieStreamEntity(
    @PrimaryKey val id: String,
    val title: String,
    val normalizedTitle: String,
    val streamUrl: String,
    val posterUrl: String,
    val backdropUrl: String,
    val categoryId: String,
    val categoryName: String,
    val description: String,
    val year: String,
    val duration: String,
    val genre: String,
    val rating: String,
    val cast: String,
    val director: String,
    val isAdult: Boolean,
    val hidden: Boolean = false,
    val updatedAt: Long
)

@Entity(
    tableName = "series_streams",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["normalizedTitle"]),
        Index(value = ["title"])
    ]
)
data class SeriesStreamEntity(
    @PrimaryKey val id: String,
    val title: String,
    val normalizedTitle: String,
    val posterUrl: String,
    val backdropUrl: String,
    val categoryId: String,
    val categoryName: String,
    val description: String,
    val year: String,
    val genre: String,
    val rating: String,
    val cast: String,
    val director: String,
    val isAdult: Boolean,
    val hidden: Boolean = false,
    val updatedAt: Long
)

@Entity(
    tableName = "series_seasons",
    indices = [
        Index(value = ["seriesId"])
    ]
)
data class SeriesSeasonEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val title: String,
    val updatedAt: Long
)

@Entity(
    tableName = "series_episodes",
    indices = [
        Index(value = ["seriesId", "seasonNumber"]),
        Index(value = ["seasonId"])
    ]
)
data class SeriesEpisodeEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val seasonId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val normalizedTitle: String,
    val streamUrl: String,
    val description: String,
    val duration: String,
    val posterUrl: String,
    val updatedAt: Long
)

// --- DAOs ---

@Dao
interface IptvDao {

    // Session
    @Query("SELECT * FROM sessions LIMIT 1")
    fun getSessionFlow(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions LIMIT 1")
    suspend fun getSessionDirect(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("DELETE FROM sessions")
    suspend fun clearSession()

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE contentType = :type ORDER BY addedAt DESC")
    fun getFavoritesByType(type: String): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE contentId = :contentId AND contentType = :type")
    suspend fun removeFavorite(contentId: String, type: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :contentId AND contentType = :type)")
    fun isFavorite(contentId: String, type: String): Flow<Boolean>

    // Continue Watching
    @Query("SELECT * FROM continue_watching ORDER BY updatedAt DESC")
    fun getContinueWatchingFlow(): Flow<List<ContinueWatchingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveContinueWatching(progress: ContinueWatchingEntity)

    @Query("DELETE FROM continue_watching WHERE contentId = :contentId")
    suspend fun removeContinueWatching(contentId: String)

    // Recently Watched
    @Query("SELECT * FROM recently_watched ORDER BY watchedAt DESC LIMIT 20")
    fun getRecentlyWatchedFlow(): Flow<List<RecentlyWatchedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRecentlyWatched(recent: RecentlyWatchedEntity)

    @Query("DELETE FROM recently_watched WHERE contentId = :contentId")
    suspend fun deleteRecent(contentId: String)

    // EPG
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND endTime > :now ORDER BY startTime ASC")
    fun getEpgForChannel(channelId: String, now: Long): Flow<List<EpgProgramEntity>>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime <= :time AND endTime >= :time LIMIT 1")
    suspend fun getCurrentProgram(channelId: String, time: Long): EpgProgramEntity?

    @Query("SELECT * FROM epg_programs WHERE endTime >= :fromTime AND startTime <= :toTime")
    suspend fun getEpgProgramsInWindow(fromTime: Long, toTime: Long): List<EpgProgramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE endTime < :now")
    suspend fun pruneOldEpg(now: Long)

    // Parental Controls
    @Query("SELECT * FROM parental_controls LIMIT 1")
    fun getParentalSettingsFlow(): Flow<ParentalControlEntity?>

    @Query("SELECT * FROM parental_controls LIMIT 1")
    suspend fun getParentalSettingsDirect(): ParentalControlEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveParentalSettings(settings: ParentalControlEntity)

    @Query("DELETE FROM parental_controls")
    suspend fun clearParentalSettings()

    // --- IPTV Provider Content Cache DAOs ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLiveChannels(channels: List<LiveChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovies(movies: List<MovieStreamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(series: List<SeriesStreamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasons(seasons: List<SeriesSeasonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(episodes: List<SeriesEpisodeEntity>)

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type AND hidden = 0 ORDER BY pinned DESC, sortOrder ASC, name ASC")
    fun observeVisibleCategories(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY pinned DESC, sortOrder ASC, name ASC")
    fun observeAllCategoriesForManagement(type: String): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET hidden = :hidden WHERE type = :type AND id = :categoryId")
    suspend fun setCategoryHidden(type: String, categoryId: String, hidden: Boolean)

    @Query("UPDATE categories SET pinned = :pinned WHERE type = :type AND id = :categoryId")
    suspend fun setCategoryPinned(type: String, categoryId: String, pinned: Boolean)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE type = :type AND id = :categoryId")
    suspend fun updateCategorySortOrderSingle(type: String, categoryId: String, sortOrder: Int)

    @Query("SELECT * FROM live_channels WHERE (:categoryId IS NULL OR :categoryId = '' OR categoryId = :categoryId) ORDER BY sortOrder ASC, name ASC")
    fun observeLiveChannels(categoryId: String?): Flow<List<LiveChannelEntity>>

    @Query("SELECT * FROM movie_streams WHERE (:categoryId IS NULL OR :categoryId = '' OR categoryId = :categoryId) ORDER BY title ASC LIMIT :limit OFFSET :offset")
    fun observeMovies(categoryId: String?, limit: Int, offset: Int): Flow<List<MovieStreamEntity>>

    @Query("SELECT * FROM series_streams WHERE (:categoryId IS NULL OR :categoryId = '' OR categoryId = :categoryId) ORDER BY title ASC LIMIT :limit OFFSET :offset")
    fun observeSeries(categoryId: String?, limit: Int, offset: Int): Flow<List<SeriesStreamEntity>>

    @Query("SELECT * FROM series_seasons WHERE seriesId = :seriesId ORDER BY seasonNumber ASC")
    fun observeSeasons(seriesId: String): Flow<List<SeriesSeasonEntity>>

    @Query("SELECT * FROM series_episodes WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber ORDER BY episodeNumber ASC")
    fun observeEpisodes(seriesId: String, seasonNumber: Int): Flow<List<SeriesEpisodeEntity>>

    @Query("SELECT * FROM live_channels WHERE name LIKE :query ORDER BY name ASC LIMIT :limit")
    suspend fun searchLiveChannels(query: String, limit: Int): List<LiveChannelEntity>

    @Query("SELECT * FROM movie_streams WHERE title LIKE :query OR normalizedTitle LIKE :query ORDER BY title ASC LIMIT :limit")
    suspend fun searchMovies(query: String, limit: Int): List<MovieStreamEntity>

    @Query("SELECT * FROM series_streams WHERE title LIKE :query OR normalizedTitle LIKE :query ORDER BY title ASC LIMIT :limit")
    suspend fun searchSeries(query: String, limit: Int): List<SeriesStreamEntity>

    @Query("SELECT * FROM movie_streams WHERE normalizedTitle = :normalizedTitle LIMIT 1")
    suspend fun findBestMovieMatch(normalizedTitle: String): MovieStreamEntity?

    @Query("SELECT * FROM movie_streams WHERE normalizedTitle LIKE :query LIMIT :limit")
    suspend fun findMovieMatchesLike(query: String, limit: Int): List<MovieStreamEntity>

    @Query("SELECT * FROM series_streams WHERE normalizedTitle = :normalizedTitle LIMIT 1")
    suspend fun findBestSeriesMatch(normalizedTitle: String): SeriesStreamEntity?

    @Query("SELECT * FROM series_streams WHERE normalizedTitle LIKE :query LIMIT :limit")
    suspend fun findSeriesMatchesLike(query: String, limit: Int): List<SeriesStreamEntity>

    @Query("DELETE FROM categories WHERE type = :type")
    suspend fun clearCategoriesByType(type: String)

    @Query("DELETE FROM live_channels WHERE (:categoryId IS NULL OR :categoryId = '' OR categoryId = :categoryId)")
    suspend fun clearLiveChannels(categoryId: String?)

    @Query("DELETE FROM movie_streams WHERE (:categoryId IS NULL OR :categoryId = '' OR categoryId = :categoryId)")
    suspend fun clearMovies(categoryId: String?)

    @Query("DELETE FROM series_streams WHERE (:categoryId IS NULL OR :categoryId = '' OR categoryId = :categoryId)")
    suspend fun clearSeries(categoryId: String?)

    @Query("DELETE FROM series_seasons WHERE seriesId = :seriesId")
    suspend fun clearSeasons(seriesId: String)

    @Query("DELETE FROM series_episodes WHERE seriesId = :seriesId")
    suspend fun clearEpisodes(seriesId: String)

    @Query("SELECT MAX(updatedAt) FROM categories WHERE type = :type")
    suspend fun getMaxCategoryUpdatedAt(type: String): Long?

    @Query("SELECT MAX(updatedAt) FROM live_channels WHERE categoryId = :categoryId")
    suspend fun getMaxLiveChannelUpdatedAt(categoryId: String): Long?

    @Query("SELECT MAX(updatedAt) FROM live_channels")
    suspend fun getMaxLiveChannelUpdatedAtGlobal(): Long?

    @Query("SELECT MAX(updatedAt) FROM movie_streams WHERE categoryId = :categoryId")
    suspend fun getMaxMovieUpdatedAt(categoryId: String): Long?

    @Query("SELECT MAX(updatedAt) FROM series_streams WHERE categoryId = :categoryId")
    suspend fun getMaxSeriesUpdatedAt(categoryId: String): Long?

    @Query("SELECT MAX(updatedAt) FROM series_seasons WHERE seriesId = :seriesId")
    suspend fun getMaxSeasonUpdatedAt(seriesId: String): Long?

    @Query("SELECT MAX(updatedAt) FROM series_episodes WHERE seriesId = :seriesId")
    suspend fun getMaxEpisodeUpdatedAt(seriesId: String): Long?
}

// --- Database ---

@Database(
    entities = [
        SessionEntity::class,
        FavoriteEntity::class,
        ContinueWatchingEntity::class,
        RecentlyWatchedEntity::class,
        EpgProgramEntity::class,
        ParentalControlEntity::class,
        CategoryEntity::class,
        LiveChannelEntity::class,
        MovieStreamEntity::class,
        SeriesStreamEntity::class,
        SeriesSeasonEntity::class,
        SeriesEpisodeEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao

    companion object {
        @Volatile
        private var INSTANCE: IptvDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create categories table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `sortOrder` INTEGER NOT NULL, 
                        `hidden` INTEGER NOT NULL, 
                        `pinned` INTEGER NOT NULL DEFAULT 0, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`, `type`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_type` ON `categories` (`type`)")

                // Create live_channels table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `live_channels` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `streamUrl` TEXT NOT NULL, 
                        `logoUrl` TEXT NOT NULL, 
                        `categoryId` TEXT NOT NULL, 
                        `categoryName` TEXT NOT NULL, 
                        `epgId` TEXT NOT NULL, 
                        `channelNumber` INTEGER NOT NULL, 
                        `isLocked` INTEGER NOT NULL, 
                        `isAdult` INTEGER NOT NULL, 
                        `hasCatchup` INTEGER NOT NULL, 
                        `hidden` INTEGER NOT NULL, 
                        `sortOrder` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_live_channels_categoryId` ON `live_channels` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_live_channels_name` ON `live_channels` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_live_channels_epgId` ON `live_channels` (`epgId`)")

                // Create movie_streams table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `movie_streams` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `normalizedTitle` TEXT NOT NULL, 
                        `streamUrl` TEXT NOT NULL, 
                        `posterUrl` TEXT NOT NULL, 
                        `backdropUrl` TEXT NOT NULL, 
                        `categoryId` TEXT NOT NULL, 
                        `categoryName` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `year` TEXT NOT NULL, 
                        `duration` TEXT NOT NULL, 
                        `genre` TEXT NOT NULL, 
                        `rating` TEXT NOT NULL, 
                        `cast` TEXT NOT NULL, 
                        `director` TEXT NOT NULL, 
                        `isAdult` INTEGER NOT NULL, 
                        `hidden` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_movie_streams_categoryId` ON `movie_streams` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_movie_streams_normalizedTitle` ON `movie_streams` (`normalizedTitle`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_movie_streams_title` ON `movie_streams` (`title`)")

                // Create series_streams table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `series_streams` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `normalizedTitle` TEXT NOT NULL, 
                        `posterUrl` TEXT NOT NULL, 
                        `backdropUrl` TEXT NOT NULL, 
                        `categoryId` TEXT NOT NULL, 
                        `categoryName` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `year` TEXT NOT NULL, 
                        `genre` TEXT NOT NULL, 
                        `rating` TEXT NOT NULL, 
                        `cast` TEXT NOT NULL, 
                        `director` TEXT NOT NULL, 
                        `isAdult` INTEGER NOT NULL, 
                        `hidden` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_streams_categoryId` ON `series_streams` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_streams_normalizedTitle` ON `series_streams` (`normalizedTitle`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_streams_title` ON `series_streams` (`title`)")

                // Create series_seasons table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `series_seasons` (
                        `id` TEXT NOT NULL, 
                        `seriesId` TEXT NOT NULL, 
                        `seasonNumber` INTEGER NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_seasons_seriesId` ON `series_seasons` (`seriesId`)")

                // Create series_episodes table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `series_episodes` (
                        `id` TEXT NOT NULL, 
                        `seriesId` TEXT NOT NULL, 
                        `seasonId` TEXT NOT NULL, 
                        `seasonNumber` INTEGER NOT NULL, 
                        `episodeNumber` INTEGER NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `normalizedTitle` TEXT NOT NULL, 
                        `streamUrl` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `duration` TEXT NOT NULL, 
                        `posterUrl` TEXT NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_episodes_seriesId_seasonNumber` ON `series_episodes` (`seriesId`, `seasonNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_episodes_seasonId` ON `series_episodes` (`seasonId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): IptvDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IptvDatabase::class.java,
                    "iptv_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
