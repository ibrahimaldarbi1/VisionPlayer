package com.example.data

import android.content.Context
import androidx.room.*
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
}

// --- Database ---

@Database(
    entities = [
        SessionEntity::class,
        FavoriteEntity::class,
        ContinueWatchingEntity::class,
        RecentlyWatchedEntity::class,
        EpgProgramEntity::class,
        ParentalControlEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao

    companion object {
        @Volatile
        private var INSTANCE: IptvDatabase? = null

        fun getDatabase(context: Context): IptvDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IptvDatabase::class.java,
                    "iptv_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
