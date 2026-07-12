package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.ContinueWatchingEntity
import com.example.data.FavoriteEntity
import com.example.data.HomeItem
import com.example.data.HomeResponse
import com.example.data.IptvRepository
import com.example.data.Movie
import com.example.data.RecentlyWatchedEntity
import com.example.data.Series
import com.example.data.TmdbClickDecisionProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val homeResponse: HomeResponse) : HomeUiState()
    object Empty : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

sealed class HomeEvent {
    data class PlayMovie(val movie: Movie) : HomeEvent()
    data class OpenSeries(val series: Series) : HomeEvent()
}

class HomeViewModel(private val repository: IptvRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var favoritesJob: Job? = null
    private var continueWatchingJob: Job? = null
    private var recentlyWatchedJob: Job? = null
    private var loadHomeJob: Job? = null

    private val _favorites = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    val favorites: StateFlow<List<FavoriteEntity>> = _favorites.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<ContinueWatchingEntity>>(emptyList())
    val continueWatching: StateFlow<List<ContinueWatchingEntity>> = _continueWatching.asStateFlow()

    private val _recentlyWatched = MutableStateFlow<List<RecentlyWatchedEntity>>(emptyList())
    val recentlyWatched: StateFlow<List<RecentlyWatchedEntity>> = _recentlyWatched.asStateFlow()

    private val _unavailableTmdbItem = MutableStateFlow<HomeItem?>(null)
    val unavailableTmdbItem: StateFlow<HomeItem?> = _unavailableTmdbItem.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private var currentProfile: ProviderProfile? = null

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile
        
        if (oldProfile?.providerId != profile.providerId) {
            loadHomeData(profile.providerId)
            _unavailableTmdbItem.value = null
        }

        // Handle Favorites subscription
        if (profile.features.favoritesEnabled) {
            if (favoritesJob == null || oldProfile?.providerId != profile.providerId) {
                favoritesJob?.cancel()
                favoritesJob = viewModelScope.launch {
                    repository.favorites.collect { list ->
                        _favorites.value = list
                    }
                }
            }
        } else {
            favoritesJob?.cancel()
            favoritesJob = null
            _favorites.value = emptyList()
        }

        // Handle Continue Watching subscription
        if (profile.features.continueWatchingEnabled) {
            if (continueWatchingJob == null || oldProfile?.providerId != profile.providerId) {
                continueWatchingJob?.cancel()
                continueWatchingJob = viewModelScope.launch {
                    repository.continueWatching.collect { list ->
                        _continueWatching.value = list
                    }
                }
            }
        } else {
            continueWatchingJob?.cancel()
            continueWatchingJob = null
            _continueWatching.value = emptyList()
        }

        // Handle Recently Watched subscription
        if (profile.features.recentlyWatchedEnabled) {
            if (recentlyWatchedJob == null || oldProfile?.providerId != profile.providerId) {
                recentlyWatchedJob?.cancel()
                recentlyWatchedJob = viewModelScope.launch {
                    repository.recentlyWatched.collect { list ->
                        _recentlyWatched.value = list
                    }
                }
            }
        } else {
            recentlyWatchedJob?.cancel()
            recentlyWatchedJob = null
            _recentlyWatched.value = emptyList()
        }
    }

    fun loadHomeData(providerId: String) {
        loadHomeJob?.cancel()
        _uiState.value = HomeUiState.Loading
        loadHomeJob = viewModelScope.launch {
            repository.loadHome(providerId)
                .onSuccess { response ->
                    val rows = response.rows ?: emptyList()
                    val validRows = rows.filter { row ->
                        row.items != null && row.items.isNotEmpty()
                    }
                    if (validRows.isEmpty()) {
                        _uiState.value = HomeUiState.Empty
                    } else {
                        _uiState.value = HomeUiState.Success(response.copy(rows = validRows))
                    }
                }
                .onFailure { error ->
                    _uiState.value = HomeUiState.Error(error.message ?: "Failed to connect to trending backend service.")
                }
        }
    }

    fun toggleFavorite(favorite: FavoriteEntity) {
        val profile = currentProfile ?: return
        if (!profile.features.favoritesEnabled) return
        viewModelScope.launch {
            val isFav = _favorites.value.any { it.contentId == favorite.contentId && it.contentType == favorite.contentType }
            if (isFav) {
                repository.removeFavorite(favorite.contentId, favorite.contentType)
            } else {
                repository.addFavorite(favorite)
            }
        }
    }

    fun dismissUnavailableDialog() {
        _unavailableTmdbItem.value = null
    }

    fun onTmdbItemClick(item: HomeItem) {
        val itemTitle = item.title ?: "Untitled"
        viewModelScope.launch {
            if (item.mediaType == "tv") {
                val matched = repository.findMatchingSeries(itemTitle)
                val decision = TmdbClickDecisionProcessor.processClick(item, null, matched)
                when (decision) {
                    is TmdbClickDecisionProcessor.TmdbClickResult.OpenSeries -> {
                        _events.emit(HomeEvent.OpenSeries(decision.series))
                    }
                    is TmdbClickDecisionProcessor.TmdbClickResult.Unavailable -> {
                        _unavailableTmdbItem.value = decision.item
                    }
                    else -> {}
                }
            } else {
                val matched = repository.findMatchingMovie(itemTitle)
                val decision = TmdbClickDecisionProcessor.processClick(item, matched, null)
                when (decision) {
                    is TmdbClickDecisionProcessor.TmdbClickResult.PlayMovie -> {
                        _events.emit(HomeEvent.PlayMovie(decision.movie))
                    }
                    is TmdbClickDecisionProcessor.TmdbClickResult.Unavailable -> {
                        _unavailableTmdbItem.value = decision.item
                    }
                    else -> {}
                }
            }
        }
    }
}
