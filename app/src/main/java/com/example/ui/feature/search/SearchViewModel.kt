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
    val favorites: List<FavoriteEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SearchViewModel(private val repository: IptvRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var currentProfile: ProviderProfile? = null
    
    private var favoritesJob: Job? = null
    private var observeInputJob: Job? = null
    private var searchJob: Job? = null

    private val liveCategories = MutableStateFlow<List<Category>>(emptyList())
    private val movieCategories = MutableStateFlow<List<Category>>(emptyList())
    private val seriesCategories = MutableStateFlow<List<Category>>(emptyList())

    private var liveCatsJob: Job? = null
    private var movieCatsJob: Job? = null
    private var seriesCatsJob: Job? = null

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile

        if (oldProfile?.providerId != profile.providerId) {
            _query.value = ""
            _uiState.update { SearchUiState(query = "") }
        }

        if (profile.features.searchEnabled) {
            // Subscribe to favorites
            if (profile.features.favoritesEnabled) {
                if (favoritesJob == null || oldProfile?.providerId != profile.providerId) {
                    favoritesJob?.cancel()
                    favoritesJob = viewModelScope.launch {
                        repository.favorites.collect { favs ->
                            _uiState.update { it.copy(favorites = favs) }
                        }
                    }
                }
            } else {
                favoritesJob?.cancel()
                favoritesJob = null
                _uiState.update { it.copy(favorites = emptyList()) }
            }

            // Observe Categories
            if (profile.features.liveTvEnabled) {
                if (liveCatsJob == null || oldProfile?.providerId != profile.providerId) {
                    liveCatsJob?.cancel()
                    liveCatsJob = viewModelScope.launch {
                        repository.observeVisibleCategories("LIVE").collect { cats ->
                            liveCategories.value = cats
                        }
                    }
                }
            } else {
                liveCatsJob?.cancel()
                liveCatsJob = null
                liveCategories.value = emptyList()
            }

            if (profile.features.moviesEnabled) {
                if (movieCatsJob == null || oldProfile?.providerId != profile.providerId) {
                    movieCatsJob?.cancel()
                    movieCatsJob = viewModelScope.launch {
                        repository.observeVisibleCategories("MOVIE").collect { cats ->
                            movieCategories.value = cats
                        }
                    }
                }
            } else {
                movieCatsJob?.cancel()
                movieCatsJob = null
                movieCategories.value = emptyList()
            }

            if (profile.features.seriesEnabled) {
                if (seriesCatsJob == null || oldProfile?.providerId != profile.providerId) {
                    seriesCatsJob?.cancel()
                    seriesCatsJob = viewModelScope.launch {
                        repository.observeVisibleCategories("SERIES").collect { cats ->
                            seriesCategories.value = cats
                        }
                    }
                }
            } else {
                seriesCatsJob?.cancel()
                seriesCatsJob = null
                seriesCategories.value = emptyList()
            }

            // Combine inputs for search
            if (observeInputJob == null || oldProfile?.providerId != profile.providerId) {
                observeInputJob?.cancel()
                observeInputJob = viewModelScope.launch {
                    combine(
                        _query,
                        liveCategories,
                        movieCategories,
                        seriesCategories
                    ) { queryText, liveCats, movieCats, seriesCats ->
                        SearchInput(queryText, liveCats, movieCats, seriesCats)
                    }.collect { input ->
                        _uiState.update { it.copy(query = input.queryText) }
                        performSearch(input)
                    }
                }
            }
        } else {
            // Feature disabled: stop all observations and clear state
            favoritesJob?.cancel()
            favoritesJob = null
            observeInputJob?.cancel()
            observeInputJob = null
            searchJob?.cancel()
            searchJob = null
            liveCatsJob?.cancel()
            liveCatsJob = null
            movieCatsJob?.cancel()
            movieCatsJob = null
            seriesCatsJob?.cancel()
            seriesCatsJob = null

            _query.value = ""
            _uiState.value = SearchUiState()
        }
    }

    private data class SearchInput(
        val queryText: String,
        val liveCats: List<Category>,
        val movieCats: List<Category>,
        val seriesCats: List<Category>
    )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun toggleFavorite(favorite: FavoriteEntity) {
        val profile = currentProfile ?: return
        if (!profile.features.searchEnabled || !profile.features.favoritesEnabled) return
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
            _uiState.update { it.copy(results = SearchResults(), isLoading = false, error = null) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        val searchForQuery = input.queryText
        val searchForProviderId = profile.providerId

        searchJob = viewModelScope.launch {
            try {
                repository.searchContent(
                    input.queryText,
                    profile.features.liveTvEnabled,
                    profile.features.moviesEnabled,
                    profile.features.seriesEnabled
                ).collect { results ->
                    if (_query.value == searchForQuery && currentProfile?.providerId == searchForProviderId) {
                        val visibleLiveIds = input.liveCats.map { it.id }.toSet()
                        val visibleMovieIds = input.movieCats.map { it.id }.toSet()
                        val visibleSeriesIds = input.seriesCats.map { it.id }.toSet()

                        val filteredResults = results.copy(
                            liveChannels = results.liveChannels.filter { it.categoryId in visibleLiveIds },
                            movies = results.movies.filter { it.categoryId in visibleMovieIds },
                            series = results.series.filter { it.categoryId in visibleSeriesIds }
                        )
                        _uiState.update {
                            it.copy(
                                results = filteredResults,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_query.value == searchForQuery && currentProfile?.providerId == searchForProviderId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Search failed"
                        )
                    }
                }
            }
        }
    }
}
