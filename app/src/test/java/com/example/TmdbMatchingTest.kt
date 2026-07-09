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
}
