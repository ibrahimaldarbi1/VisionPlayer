package com.example.data

import com.example.data.Movie
import com.example.data.Series
import com.example.data.HomeItem

object TmdbClickDecisionProcessor {
    sealed class TmdbClickResult {
        data class PlayMovie(val movie: Movie) : TmdbClickResult()
        data class OpenSeries(val series: Series) : TmdbClickResult()
        data class Unavailable(val item: HomeItem) : TmdbClickResult()
    }

    fun processClick(
        item: HomeItem,
        matchedMovie: Movie?,
        matchedSeries: Series?
    ): TmdbClickResult {
        return if (item.mediaType == "tv") {
            if (matchedSeries != null) {
                TmdbClickResult.OpenSeries(matchedSeries)
            } else {
                TmdbClickResult.Unavailable(item)
            }
        } else {
            if (matchedMovie != null) {
                TmdbClickResult.PlayMovie(matchedMovie)
            } else {
                TmdbClickResult.Unavailable(item)
            }
        }
    }
}
