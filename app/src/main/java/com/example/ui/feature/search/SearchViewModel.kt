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

    private var favoriteMutationGeneration = 0
    private data class FavoriteMutationIdentity(
        val profileId: String,
        val providerId: String,
        val favoritesEnabled: Boolean,
        val relevantContentEnabled: Boolean,
        val generation: Int
    )

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile

        val identityChanged = oldProfile?.id != profile.id ||
            oldProfile.providerId != profile.providerId ||
            oldProfile.features.searchEnabled != profile.features.searchEnabled ||
            oldProfile.features.liveTvEnabled != profile.features.liveTvEnabled ||
            oldProfile.features.moviesEnabled != profile.features.moviesEnabled ||
            oldProfile.features.seriesEnabled != profile.features.seriesEnabled
            
        if (identityChanged) {
            searchJob?.cancel()
            activeRequestIdentity = null
            favoriteMutationJob?.cancel()
            favoriteMutationGeneration++
            
            liveCatsJob?.cancel()
            movieCatsJob?.cancel()
            seriesCatsJob?.cancel()
            observeInputJob?.cancel()
            
            liveCatsJob = null
            movieCatsJob = null
            seriesCatsJob = null
            observeInputJob = null

            if (!profile.features.searchEnabled) {
                _query.value = ""
                _uiState.update { SearchUiState(query = "") }
            }
        }
        
        if (profile.features.searchEnabled) {
            // Subscribe to favorites
            if (profile.features.favoritesEnabled) {
                if (favoritesJob == null || oldProfile?.providerId != profile.providerId || oldProfile?.id != profile.id) {
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
                if (liveCatsJob == null || oldProfile?.providerId != profile.providerId || oldProfile?.id != profile.id) {
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
                if (movieCatsJob == null || oldProfile?.providerId != profile.providerId || oldProfile?.id != profile.id) {
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
                if (seriesCatsJob == null || oldProfile?.providerId != profile.providerId || oldProfile?.id != profile.id) {
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
            if (observeInputJob == null || oldProfile?.providerId != profile.providerId || oldProfile?.id != profile.id) {
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

    private var favoriteMutationJob: Job? = null

    fun toggleFavorite(favorite: FavoriteEntity) {
        val profile = currentProfile ?: return
        
        val relevantEnabled = when (favorite.contentType) {
            "LIVE" -> profile.features.liveTvEnabled
            "MOVIE" -> profile.features.moviesEnabled
            "SERIES", "EPISODE" -> profile.features.seriesEnabled
            else -> false
        }
        
        val identity = FavoriteMutationIdentity(
            profileId = profile.id,
            providerId = profile.providerId,
            favoritesEnabled = profile.features.favoritesEnabled,
            relevantContentEnabled = relevantEnabled,
            generation = favoriteMutationGeneration
        )

        favoriteMutationJob?.cancel()
        favoriteMutationJob = viewModelScope.launch {
            val current = currentProfile ?: return@launch
            val currentRelevantEnabled = when (favorite.contentType) {
                "LIVE" -> current.features.liveTvEnabled
                "MOVIE" -> current.features.moviesEnabled
                "SERIES", "EPISODE" -> current.features.seriesEnabled
                else -> false
            }
            val currentIdentity = FavoriteMutationIdentity(
                profileId = current.id,
                providerId = current.providerId,
                favoritesEnabled = current.features.favoritesEnabled,
                relevantContentEnabled = currentRelevantEnabled,
                generation = favoriteMutationGeneration
            )
            
            if (identity != currentIdentity) return@launch
            if (!identity.favoritesEnabled || !identity.relevantContentEnabled) return@launch

            val isFav = _uiState.value.favorites.any {
                it.contentId == favorite.contentId && it.contentType == favorite.contentType
            }
            try {
                if (isFav) {
                    repository.removeFavorite(favorite.contentId, favorite.contentType)
                } else {
                    repository.addFavorite(favorite)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore failure
            }
        }
    }

    private data class SearchRequestIdentity(
        val profileId: String,
        val providerId: String,
        val query: String,
        val searchEnabled: Boolean,
        val liveTvEnabled: Boolean,
        val moviesEnabled: Boolean,
        val seriesEnabled: Boolean
    )

    private var activeRequestIdentity: SearchRequestIdentity? = null

    private fun performSearch(input: SearchInput) {
        searchJob?.cancel()
        val profile = currentProfile
        if (profile == null || !profile.features.searchEnabled || input.queryText.isBlank()) {
            activeRequestIdentity = null
            _uiState.update { it.copy(results = SearchResults(), isLoading = false, error = null) }
            return
        }

        val identity = SearchRequestIdentity(
            profileId = profile.id,
            providerId = profile.providerId,
            query = input.queryText,
            searchEnabled = profile.features.searchEnabled,
            liveTvEnabled = profile.features.liveTvEnabled,
            moviesEnabled = profile.features.moviesEnabled,
            seriesEnabled = profile.features.seriesEnabled
        )
        activeRequestIdentity = identity

        _uiState.update { it.copy(isLoading = true, error = null) }

        searchJob = viewModelScope.launch {
            try {
                repository.searchContent(
                    identity.query,
                    identity.liveTvEnabled,
                    identity.moviesEnabled,
                    identity.seriesEnabled
                ).collect { results ->
                    if (activeRequestIdentity == identity) {
                        val visibleLiveIds = input.liveCats.map { it.id }.toSet()
                        val visibleMovieIds = input.movieCats.map { it.id }.toSet()
                        val visibleSeriesIds = input.seriesCats.map { it.id }.toSet()

                        val filteredResults = results.copy(
                            liveChannels = if (identity.liveTvEnabled) results.liveChannels.filter { it.categoryId in visibleLiveIds } else emptyList(),
                            movies = if (identity.moviesEnabled) results.movies.filter { it.categoryId in visibleMovieIds } else emptyList(),
                            series = if (identity.seriesEnabled) results.series.filter { it.categoryId in visibleSeriesIds } else emptyList()
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
                if (activeRequestIdentity == identity) {
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

    fun retry() {
        val input = SearchInput(
            queryText = _query.value,
            liveCats = liveCategories.value,
            movieCats = movieCategories.value,
            seriesCats = seriesCategories.value
        )
        performSearch(input)
    }
}
