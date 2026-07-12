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
            oldProfile.features.favoritesEnabled != profile.features.favoritesEnabled ||
            oldProfile.features.moviesEnabled != profile.features.moviesEnabled
            
        if (identityChanged) {
            favoriteMutationJob?.cancel()
            favoriteMutationGeneration++
        }

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
            if (favorite.contentType != "MOVIE") return@launch
            
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
                // ignore
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
            val originalProviderId = profile.providerId
            val originalCategoryId = _uiState.value.selectedCategoryId
            try {
                repository.getCategories("MOVIE").collect {} // Seed cache fully without cancelling
                
                if (currentProfile?.providerId == originalProviderId && _uiState.value.selectedCategoryId == originalCategoryId && currentProfile?.features?.moviesEnabled == true) {
                    repository.getMovies(originalCategoryId).collect { moviesList ->
                        if (currentProfile?.providerId == originalProviderId && _uiState.value.selectedCategoryId == originalCategoryId && currentProfile?.features?.moviesEnabled == true) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    movies = moviesList,
                                    error = null
                                )
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentProfile?.providerId == originalProviderId && _uiState.value.selectedCategoryId == originalCategoryId && currentProfile?.features?.moviesEnabled == true) {
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
}
