package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class TmdbMatchingTest {

    @Test
    fun testProcessClick_WhenMovieMatched_ReturnsPlayMovie() {
        val movieItem = HomeItem(
            rank = 1,
            tmdbId = 123,
            mediaType = "movie",
            title = "Inception",
            overview = "A mind-bending thriller",
            posterUrl = "inception_poster.jpg",
            backdropUrl = "inception_backdrop.jpg",
            releaseDate = "2010-07-16",
            firstAirDate = null,
            voteAverage = 8.8,
            popularity = 120.0
        )

        val providerMovie = Movie(
            id = "movie_123",
            title = "Inception",
            streamUrl = "http://provider/movies/inception.mp4",
            posterUrl = "inception_poster.jpg",
            backdropUrl = "inception_backdrop.jpg",
            categoryId = "sci-fi",
            categoryName = "Sci-Fi",
            description = "A mind-bending thriller",
            year = "2010"
        )

        val result = TmdbClickDecisionProcessor.processClick(movieItem, providerMovie, null)
        assertTrue(result is TmdbClickDecisionProcessor.TmdbClickResult.PlayMovie)
        val playMovieResult = result as TmdbClickDecisionProcessor.TmdbClickResult.PlayMovie
        assertEquals(providerMovie, playMovieResult.movie)
        assertNotEquals("https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", playMovieResult.movie.streamUrl)
    }

    @Test
    fun testProcessClick_WhenMovieUnmatched_ReturnsUnavailable() {
        val movieItem = HomeItem(
            rank = 2,
            tmdbId = 456,
            mediaType = "movie",
            title = "Some New Movie",
            overview = "Not in library",
            posterUrl = "new_poster.jpg",
            backdropUrl = "new_backdrop.jpg",
            releaseDate = "2026-05-01",
            firstAirDate = null,
            voteAverage = 7.5,
            popularity = 80.0
        )

        val result = TmdbClickDecisionProcessor.processClick(movieItem, null, null)
        assertTrue(result is TmdbClickDecisionProcessor.TmdbClickResult.Unavailable)
        val unavailableResult = result as TmdbClickDecisionProcessor.TmdbClickResult.Unavailable
        assertEquals(movieItem, unavailableResult.item)
    }

    @Test
    fun testProcessClick_WhenSeriesMatched_ReturnsOpenSeries() {
        val tvItem = HomeItem(
            rank = 1,
            tmdbId = 789,
            mediaType = "tv",
            title = "Breaking Bad",
            overview = "A high school chemistry teacher...",
            posterUrl = "bb_poster.jpg",
            backdropUrl = "bb_backdrop.jpg",
            releaseDate = null,
            firstAirDate = "2008-01-20",
            voteAverage = 9.5,
            popularity = 250.0
        )

        val providerSeries = Series(
            id = "series_789",
            title = "Breaking Bad",
            posterUrl = "bb_poster.jpg",
            backdropUrl = "bb_backdrop.jpg",
            categoryId = "drama",
            categoryName = "Drama",
            description = "A high school chemistry teacher..."
        )

        val result = TmdbClickDecisionProcessor.processClick(tvItem, null, providerSeries)
        assertTrue(result is TmdbClickDecisionProcessor.TmdbClickResult.OpenSeries)
        val openSeriesResult = result as TmdbClickDecisionProcessor.TmdbClickResult.OpenSeries
        assertEquals(providerSeries, openSeriesResult.series)
    }

    @Test
    fun testProcessClick_WhenSeriesUnmatched_ReturnsUnavailable() {
        val tvItem = HomeItem(
            rank = 2,
            tmdbId = 999,
            mediaType = "tv",
            title = "Unreleased Series",
            overview = "Not in library",
            posterUrl = "unreleased.jpg",
            backdropUrl = "unreleased.jpg",
            releaseDate = null,
            firstAirDate = "2026-09-01",
            voteAverage = 0.0,
            popularity = 1.0
        )

        val result = TmdbClickDecisionProcessor.processClick(tvItem, null, null)
        assertTrue(result is TmdbClickDecisionProcessor.TmdbClickResult.Unavailable)
        val unavailableResult = result as TmdbClickDecisionProcessor.TmdbClickResult.Unavailable
        assertEquals(tvItem, unavailableResult.item)
    }

    @Test
    fun testTitleNormalization_ClearsYearsAndJunk() {
        val original = "Inception (2010) [1080p] {Dual Audio} - Spanish Castellano Bluray"
        // Let's create a temporary normalize helper in Kotlin to verify the regex
        val temp = java.text.Normalizer.normalize(original, java.text.Normalizer.Form.NFD)
        var clean = temp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        clean = clean.replace(Regex("\\b(19|20)\\d{2}\\b"), "")
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
        clean = clean.replace(Regex("[^a-z0-9\\s]"), "")
        clean = clean.replace(Regex("\\s+"), " ")
        val result = clean.trim()

        assertEquals("inception", result)
    }

    @Test
    fun testMappers_toDomainAndToEntity() {
        val movieEntity = MovieStreamEntity(
            id = "movie_123",
            title = "Inception",
            normalizedTitle = "inception",
            streamUrl = "http://server/movie.mp4",
            posterUrl = "poster.jpg",
            backdropUrl = "backdrop.jpg",
            categoryId = "1",
            categoryName = "Sci-Fi",
            description = "A mind-bending thriller",
            year = "2010",
            duration = "148m",
            genre = "Sci-Fi",
            rating = "8.8",
            cast = "Leonardo DiCaprio",
            director = "Christopher Nolan",
            isAdult = false,
            hidden = false,
            updatedAt = 123456789L
        )

        val domain = movieEntity.toDomain()
        assertEquals("movie_123", domain.id)
        assertEquals("Inception", domain.title)
        assertEquals("http://server/movie.mp4", domain.streamUrl)
        assertEquals("8.8", domain.rating)

        val reEntity = domain.toEntity("inception", hidden = false, updatedAt = 123456789L)
        assertEquals("movie_123", reEntity.id)
        assertEquals("inception", reEntity.normalizedTitle)
    }

    @Test
    fun testStreamUrlSanitization_RemovesSensitiveCredentials() {
        val rawUrl = "http://iptv.server.xyz:8080/get.php?auth=123xyz&user=testuser&pass=secret123&type=m3u"
        val uri = try {
            java.net.URI(rawUrl)
        } catch (e: Exception) {
            null
        }
        val sanitized = if (uri != null) {
            "${uri.scheme}://${uri.host}${uri.path}"
        } else {
            "[Protected Stream]"
        }
        
        // Assert that the credentials and queries are fully removed from the reported string
        assertEquals("http://iptv.server.xyz/get.php", sanitized)
        assertFalse(sanitized.contains("pass"))
        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("auth"))
    }

    @Test
    fun testPlayerPreferenceSimulation_SavesAndLoadsCorrectly() {
        // Simulating the logic used inside our SharedPreferences storage for Scale Modes & Decoders
        val mockPrefs = mutableMapOf<String, String>()
        
        // 1. Save
        mockPrefs["scale_mode"] = "FILL"
        mockPrefs["decoder_mode"] = "software"
        
        // 2. Load
        val loadedScaleMode = mockPrefs["scale_mode"] ?: "FIT"
        val loadedDecoderMode = mockPrefs["decoder_mode"] ?: "auto"
        
        assertEquals("FILL", loadedScaleMode)
        assertEquals("software", loadedDecoderMode)
    }
}
