package com.example.ui.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.Category
import com.example.data.Episode
import com.example.data.FavoriteEntity
import com.example.data.IptvRepository
import com.example.data.Season
import com.example.data.Series
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesUiState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
    val seriesList: List<Series> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val error: String? = null
)

data class SeriesDetailsUiState(
    val activeSeries: Series? = null,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val selectedSeason: Season? = null,
    val isLoadingSeasons: Boolean = false,
    val isLoadingEpisodes: Boolean = false,
    val error: String? = null
)

class SeriesViewModel(private val repository: IptvRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesUiState())
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    private val _detailsUiState = MutableStateFlow(SeriesDetailsUiState())
    val detailsUiState: StateFlow<SeriesDetailsUiState> = _detailsUiState.asStateFlow()

    private var currentProfile: ProviderProfile? = null

    private var observeCategoriesJob: Job? = null
    private var observeFavoritesJob: Job? = null
    private var loadSeriesJob: Job? = null

    private var loadSeasonsJob: Job? = null
    private var loadEpisodesJob: Job? = null

    fun onProfileChanged(profile: ProviderProfile) {
        val oldProfile = currentProfile
        currentProfile = profile

        if (oldProfile?.providerId != profile.providerId) {
            _uiState.update { it.copy(selectedCategoryId = null, seriesList = emptyList(), error = null) }
            selectSeries(null)
        }

        if (profile.features.seriesEnabled) {
            // Subscribe to categories
            if (observeCategoriesJob == null || oldProfile?.providerId != profile.providerId) {
                observeCategoriesJob?.cancel()
                observeCategoriesJob = viewModelScope.launch {
                    repository.observeVisibleCategories("SERIES").collect { categories ->
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

            loadSeries()
        } else {
            // Feature disabled: cancel everything and clear state immediately
            observeCategoriesJob?.cancel()
            observeCategoriesJob = null
            observeFavoritesJob?.cancel()
            observeFavoritesJob = null
            loadSeriesJob?.cancel()
            loadSeriesJob = null
            
            _uiState.update { SeriesUiState() }
            selectSeries(null)
        }
    }

    fun selectCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        loadSeries()
    }

    fun toggleFavorite(favorite: FavoriteEntity) {
        val profile = currentProfile ?: return
        if (!profile.features.seriesEnabled || !profile.features.favoritesEnabled) return
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

    fun loadSeries() {
        val profile = currentProfile ?: return
        if (!profile.features.seriesEnabled) return

        // Cancel previous loading job to prevent old results overwriting newer state
        loadSeriesJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        loadSeriesJob = viewModelScope.launch {
            try {
                repository.getCategories("SERIES").first() // Seed cache
                val categoryId = _uiState.value.selectedCategoryId
                val originalProviderId = profile.providerId
                val originalCategoryId = categoryId

                repository.getSeries(categoryId).collect { seriesListResult ->
                    if (currentProfile?.providerId == originalProviderId && _uiState.value.selectedCategoryId == originalCategoryId) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                seriesList = seriesListResult,
                                error = null
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load series"
                    )
                }
            }
        }
    }

    // Series Details methods
    fun selectSeries(series: Series?) {
        loadSeasonsJob?.cancel()
        loadEpisodesJob?.cancel()
        _detailsUiState.update {
            SeriesDetailsUiState(activeSeries = series)
        }
        if (series != null) {
            loadSeasons(series)
        }
    }

    private fun loadSeasons(series: Series) {
        _detailsUiState.update { it.copy(isLoadingSeasons = true, error = null) }
        loadSeasonsJob?.cancel()
        loadSeasonsJob = viewModelScope.launch {
            try {
                repository.getSeasons(series.id).collect { fetchedSeasons ->
                    val currentDetails = _detailsUiState.value
                    if (currentDetails.activeSeries?.id == series.id) {
                        val previousSeason = currentDetails.selectedSeason
                        val newSelected = fetchedSeasons.find { it.id == previousSeason?.id } ?: fetchedSeasons.firstOrNull()
                        _detailsUiState.update {
                            it.copy(
                                seasons = fetchedSeasons,
                                isLoadingSeasons = false,
                                selectedSeason = newSelected
                            )
                        }
                        if (newSelected != null) {
                            loadEpisodes(series.id, newSelected)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_detailsUiState.value.activeSeries?.id == series.id) {
                    _detailsUiState.update {
                        it.copy(
                            isLoadingSeasons = false,
                            error = e.message ?: "Failed to load seasons"
                        )
                    }
                }
            }
        }
    }

    fun selectSeason(season: Season) {
        val seriesId = _detailsUiState.value.activeSeries?.id ?: return
        loadEpisodesJob?.cancel()
        _detailsUiState.update {
            it.copy(
                selectedSeason = season,
                episodes = emptyList()
            )
        }
        loadEpisodes(seriesId, season)
    }

    private fun loadEpisodes(seriesId: String, season: Season) {
        _detailsUiState.update { it.copy(isLoadingEpisodes = true, error = null) }
        loadEpisodesJob?.cancel()
        loadEpisodesJob = viewModelScope.launch {
            try {
                repository.getEpisodes(seriesId, season.id).collect { fetchedEpisodes ->
                    val currentDetails = _detailsUiState.value
                    if (currentDetails.activeSeries?.id == seriesId && currentDetails.selectedSeason?.id == season.id) {
                        _detailsUiState.update {
                            it.copy(
                                episodes = fetchedEpisodes,
                                isLoadingEpisodes = false
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentDetails = _detailsUiState.value
                if (currentDetails.activeSeries?.id == seriesId && currentDetails.selectedSeason?.id == season.id) {
                    _detailsUiState.update {
                        it.copy(
                            isLoadingEpisodes = false,
                            error = e.message ?: "Failed to load episodes"
                        )
                    }
                }
            }
        }
    }

    fun retryDetails() {
        val series = _detailsUiState.value.activeSeries ?: return
        val selectedSeason = _detailsUiState.value.selectedSeason
        if (selectedSeason != null) {
            loadEpisodes(series.id, selectedSeason)
        } else {
            loadSeasons(series)
        }
    }
}
