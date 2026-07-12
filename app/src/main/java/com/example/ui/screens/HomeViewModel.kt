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

    private data class HomeRequestIdentity(
        val profileId: String,
        val providerId: String,
        val backendBaseUrl: String,
        val moviesEnabled: Boolean,
        val seriesEnabled: Boolean
    )

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
    private var activeRequestIdentity: HomeRequestIdentity? = null
    private var matchingJob: Job? = null

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile
        
        if (oldProfile != profile) {
            matchingJob?.cancel()
            matchingJob = null
            _unavailableTmdbItem.value = null
        }

        val homeEnabled = com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldShowHomeRecommendations(profile.features)
        
        if (!homeEnabled) {
            loadHomeJob?.cancel()
            loadHomeJob = null
            activeRequestIdentity = null
            _uiState.value = HomeUiState.Empty
            _unavailableTmdbItem.value = null
        } else {
            val newIdentity = HomeRequestIdentity(
                profileId = profile.id,
                providerId = profile.providerId,
                backendBaseUrl = profile.backendBaseUrl,
                moviesEnabled = profile.features.moviesEnabled,
                seriesEnabled = profile.features.seriesEnabled
            )
            
            if (activeRequestIdentity != newIdentity) {
                activeRequestIdentity = newIdentity
                loadHomeData(newIdentity)
            }
        }
        
        // Handle Favorites subscription
        if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldCollectFavorites(profile.features)) {
            if (favoritesJob == null || oldProfile?.providerId != profile.providerId || oldProfile?.id != profile.id) {
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
        val cwEnabled = com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldCollectContinueWatching(profile.features)
        val hasContentEnabled = profile.features.moviesEnabled || profile.features.seriesEnabled
        if (cwEnabled && hasContentEnabled) {
            if (continueWatchingJob == null || oldProfile?.providerId != profile.providerId || oldProfile?.features?.moviesEnabled != profile.features.moviesEnabled || oldProfile?.features?.seriesEnabled != profile.features.seriesEnabled || oldProfile?.id != profile.id) {
                continueWatchingJob?.cancel()
                continueWatchingJob = viewModelScope.launch {
                    repository.continueWatching.collect { list ->
                        _continueWatching.value = list.filter {
                            (it.contentType == "MOVIE" && profile.features.moviesEnabled) ||
                            (it.contentType == "EPISODE" && profile.features.seriesEnabled)
                        }
                    }
                }
            }
        } else {
            continueWatchingJob?.cancel()
            continueWatchingJob = null
            _continueWatching.value = emptyList()
        }

        // Handle Recently Watched subscription
        if (com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldCollectRecentlyWatched(profile.features)) {
            if (recentlyWatchedJob == null || oldProfile?.providerId != profile.providerId || oldProfile?.id != profile.id) {
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

    private fun loadHomeData(identity: HomeRequestIdentity) {
        loadHomeJob?.cancel()
        _uiState.value = HomeUiState.Loading
        loadHomeJob = viewModelScope.launch {
            repository.loadHome(identity.providerId, identity.backendBaseUrl)
                .onSuccess { response ->
                    if (activeRequestIdentity != identity) return@onSuccess
                    
                    val rows = response.rows ?: emptyList()
                    val validRows = rows.filter { row ->
                        row.items != null && row.items.isNotEmpty() &&
                        ((row.type == "movie" && identity.moviesEnabled) || 
                         (row.type == "series" && identity.seriesEnabled) || 
                         (row.type != "movie" && row.type != "series"))
                    }

                    if (validRows.isEmpty()) {
                        _uiState.value = HomeUiState.Empty
                    } else {
                        _uiState.value = HomeUiState.Success(response.copy(rows = validRows))
                    }
                }
                .onFailure { error ->
                    if (activeRequestIdentity != identity) return@onFailure
                    _uiState.value = HomeUiState.Error(error.message ?: "Failed to connect to trending backend service.")
                }
        }
    }

    private var favoriteMutationJob: Job? = null

    fun toggleFavorite(favorite: FavoriteEntity) {
        val identity = activeRequestIdentity ?: return
        val current = currentProfile ?: return
        favoriteMutationJob?.cancel()
        favoriteMutationJob = viewModelScope.launch {
            val profile = currentProfile ?: return@launch
            if (activeRequestIdentity != identity || profile.id != current.id || profile.id != identity.profileId) return@launch
            if (!com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldCollectFavorites(profile.features)) return@launch
            
            val valid = when (favorite.contentType) {
                "LIVE" -> profile.features.liveTvEnabled
                "MOVIE" -> profile.features.moviesEnabled
                "SERIES" -> profile.features.seriesEnabled
                else -> false
            }
            if (!valid) return@launch
            
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
        val clickedProfile = currentProfile ?: return

        matchingJob?.cancel()
        matchingJob = viewModelScope.launch {
            try {
                if (item.mediaType == "tv") {
                    if (!clickedProfile.features.seriesEnabled || currentProfile?.features?.seriesEnabled != true) return@launch
                    val matched = repository.findMatchingSeries(itemTitle)
                    
                    if (currentProfile == clickedProfile && currentProfile?.features?.seriesEnabled == true) {
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
                    }
                } else {
                    if (!clickedProfile.features.moviesEnabled || currentProfile?.features?.moviesEnabled != true) return@launch
                    val matched = repository.findMatchingMovie(itemTitle)
                    
                    if (currentProfile == clickedProfile && currentProfile?.features?.moviesEnabled == true) {
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore matching errors cleanly
            }
        }
    }
}
