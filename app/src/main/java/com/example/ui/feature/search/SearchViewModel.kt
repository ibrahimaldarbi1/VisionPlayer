package com.example.ui.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.Category
import com.example.data.FavoriteEntity
import com.example.data.IptvRepository
import com.example.data.SearchResults
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val favorites: List<FavoriteEntity> = emptyList()
)

class SearchViewModel(private val repository: IptvRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var currentProfile: ProviderProfile? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.favorites.collect { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }

        viewModelScope.launch {
            combine(
                _query,
                repository.observeVisibleCategories("LIVE"),
                repository.observeVisibleCategories("MOVIE"),
                repository.observeVisibleCategories("SERIES")
            ) { queryText, liveCats, movieCats, seriesCats ->
                SearchInput(queryText, liveCats, movieCats, seriesCats)
            }.collect { input ->
                _uiState.update { it.copy(query = input.queryText) }
                performSearch(input)
            }
        }
    }

    private data class SearchInput(
        val queryText: String,
        val liveCats: List<Category>,
        val movieCats: List<Category>,
        val seriesCats: List<Category>
    )

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile
        if (oldProfile?.providerId != profile.providerId) {
            _query.value = ""
            _uiState.update { it.copy(query = "", results = SearchResults()) }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun toggleFavorite(favorite: FavoriteEntity) {
        viewModelScope.launch {
            val isFav = _uiState.value.favorites.any {
                it.contentId == favorite.contentId && it.contentType == favorite.contentType
            }
            if (isFav) {
                repository.removeFavorite(favorite.contentId, favorite.contentType)
            } else {
                repository.addFavorite(favorite)
            }
        }
    }

    private fun performSearch(input: SearchInput) {
        searchJob?.cancel()
        val profile = currentProfile
        if (profile == null || !profile.features.searchEnabled || input.queryText.isBlank()) {
            _uiState.update { it.copy(results = SearchResults()) }
            return
        }

        searchJob = viewModelScope.launch {
            repository.searchContent(
                input.queryText,
                profile.features.liveTvEnabled,
                profile.features.moviesEnabled,
                profile.features.seriesEnabled
            ).collect { results ->
                val visibleLiveIds = input.liveCats.map { it.id }.toSet()
                val visibleMovieIds = input.movieCats.map { it.id }.toSet()
                val visibleSeriesIds = input.seriesCats.map { it.id }.toSet()

                val filteredResults = results.copy(
                    liveChannels = results.liveChannels.filter { it.categoryId in visibleLiveIds },
                    movies = results.movies.filter { it.categoryId in visibleMovieIds },
                    series = results.series.filter { it.categoryId in visibleSeriesIds }
                )
                _uiState.update { it.copy(results = filteredResults) }
            }
        }
    }
}
