package com.example.ui.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.Category
import com.example.data.FavoriteEntity
import com.example.data.IptvRepository
import com.example.data.Movie
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoviesUiState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val error: String? = null
)

class MoviesViewModel(private val repository: IptvRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    private var currentProfile: ProviderProfile? = null

    private var observeCategoriesJob: Job? = null
    private var observeFavoritesJob: Job? = null
    private var loadMoviesJob: Job? = null

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile

        if (oldProfile?.providerId != profile.providerId) {
            _uiState.update { it.copy(selectedCategoryId = null, movies = emptyList(), error = null) }
        }

        if (profile.features.moviesEnabled) {
            // Subscribe to categories
            if (observeCategoriesJob == null || oldProfile?.providerId != profile.providerId) {
                observeCategoriesJob?.cancel()
                observeCategoriesJob = viewModelScope.launch {
                    repository.observeVisibleCategories("MOVIE").collect { categories ->
                        _uiState.update { it.copy(categories = categories) }
                    }
                }
            }

            // Subscribe to favorites
            if (profile.features.favoritesEnabled) {
                if (observeFavoritesJob == null || oldProfile?.providerId != profile.providerId) {
                    observeFavoritesJob?.cancel()
                    observeFavoritesJob = viewModelScope.launch {
                        repository.favorites.collect { favs ->
                            _uiState.update { it.copy(favorites = favs) }
                        }
                    }
                }
            } else {
                observeFavoritesJob?.cancel()
                observeFavoritesJob = null
                _uiState.update { it.copy(favorites = emptyList()) }
            }

            loadMovies()
        } else {
            // Feature disabled: cancel everything and clear state immediately
            observeCategoriesJob?.cancel()
            observeCategoriesJob = null
            observeFavoritesJob?.cancel()
            observeFavoritesJob = null
            loadMoviesJob?.cancel()
            loadMoviesJob = null

            _uiState.update { MoviesUiState() }
        }
    }

    fun selectCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        loadMovies()
    }

    fun toggleFavorite(favorite: FavoriteEntity) {
        val profile = currentProfile ?: return
        if (!profile.features.moviesEnabled || !profile.features.favoritesEnabled) return
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

    fun loadMovies() {
        val profile = currentProfile ?: return
        if (!profile.features.moviesEnabled) return

        // Cancel previous loading job to prevent old results overwriting newer state
        loadMoviesJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }

        loadMoviesJob = viewModelScope.launch {
            try {
                repository.getCategories("MOVIE").first() // Seed cache
                val categoryId = _uiState.value.selectedCategoryId
                val moviesList = repository.getMovies(categoryId).first()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        movies = moviesList,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load movies"
                    )
                }
            }
        }
    }
}
