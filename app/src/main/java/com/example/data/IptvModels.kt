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
}
