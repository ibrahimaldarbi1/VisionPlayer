package com.example.ui.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.Category
import com.example.data.LiveChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LiveViewModel(
    private val dataSource: LiveDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private var visibleCategoriesJob: Job? = null
    private var categoriesLoadJob: Job? = null
    private var channelsLoadJob: Job? = null

    private var currentProviderId: String? = null

    private var categoriesGeneration: Long = 0L
    private var channelsGeneration: Long = 0L

    private data class ChannelRequestKey(
        val providerId: String,
        val categoryId: String?
    )

    private var activeChannelRequestKey: ChannelRequestKey? = null
    private var rawChannels: List<LiveChannel> = emptyList()

    private fun applyVisibleCategoryFilter(
        channels: List<LiveChannel>,
        state: LiveUiState = _uiState.value
    ): List<LiveChannel> {
        if (!state.categoryVisibilityReady) {
            return channels
        }

        val visibleCategoryIds = state.categories
            .map { it.id }
            .toSet()

        return channels.filter {
            it.categoryId in visibleCategoryIds
        }
    }

    fun onProfileChanged(profile: ProviderProfile) {
        if (!profile.features.liveTvEnabled) {
            cancelVisibleCategoriesObserver()
            cancelCategoriesLoad(clearLoading = true)
            cancelChannelsLoad(clearLoading = true)
            rawChannels = emptyList()
            currentProviderId = null
            _uiState.update { currentState ->
                currentState.copy(
                    featureEnabled = false,
                    providerId = null,
                    categories = emptyList(),
                    channels = emptyList(),
                    selectedCategoryId = null,
                    categoriesError = null,
                    channelsError = null,
                    hasLoadedCategories = false,
                    hasLoadedChannels = false,
                    initialLoading = false,
                    categoriesRefreshing = false,
                    channelsRefreshing = false,
                    categoriesLoading = false,
                    channelsLoading = false,
                    categoryVisibilityReady = false
                )
            }
            return
        }

        val providerChanged = profile.providerId != currentProviderId
        val wasDisabled = !_uiState.value.featureEnabled

        if (providerChanged || wasDisabled) {
            // Cancel all old jobs
            cancelVisibleCategoriesObserver()
            cancelCategoriesLoad(clearLoading = true)
            cancelChannelsLoad(clearLoading = true)

            // Clear old provider channels and categories
            rawChannels = emptyList()

            _uiState.update { currentState ->
                currentState.copy(
                    featureEnabled = true,
                    providerId = profile.providerId,
                    categories = emptyList(),
                    channels = emptyList(),
                    selectedCategoryId = null,
                    categoriesError = null,
                    channelsError = null,
                    hasLoadedCategories = false,
                    hasLoadedChannels = false,
                    initialLoading = false,
                    categoriesRefreshing = false,
                    channelsRefreshing = false,
                    categoriesLoading = false,
                    channelsLoading = false,
                    categoryVisibilityReady = false
                )
            }

            currentProviderId = profile.providerId

            // Start visible-category observation
            observeCategoryVisibility(profile.providerId)

            // Load/seed categories
            loadCategories(profile.providerId)

            // Load channels for the selected category, initially null
            loadChannels(profile.providerId, null)
        }
    }

    private fun observeCategoryVisibility(providerId: String) {
        visibleCategoriesJob?.cancel()
        visibleCategoriesJob = viewModelScope.launch {
            try {
                dataSource.observeCategoryVisibility(providerId).collect { snapshot ->
                    if (currentProviderId != providerId) return@collect

                    if (!snapshot.isAuthoritative) {
                        _uiState.update { current ->
                            current.copy(
                                categoryVisibilityReady = false,
                                channels = rawChannels
                            )
                        }
                    } else {
                        val categories = snapshot.visibleCategories
                        val visibleIds = categories.map { it.id }.toSet()
                        val selectedCategoryId = _uiState.value.selectedCategoryId
                        val selectedStillVisible = selectedCategoryId == null || selectedCategoryId in visibleIds

                        _uiState.update { current ->
                            val updated = current.copy(
                                categories = categories,
                                categoryVisibilityReady = true,
                                categoriesError = null
                            )
                            updated.copy(
                                channels = applyVisibleCategoryFilter(rawChannels, updated)
                            )
                        }

                        if (!selectedStillVisible) {
                            _uiState.update {
                                it.copy(selectedCategoryId = null)
                            }
                            loadChannels(
                                providerId = providerId,
                                categoryId = null
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (currentProviderId == providerId) {
                    val currentReady = _uiState.value.categoryVisibilityReady
                    if (!currentReady) {
                        _uiState.update { currentState ->
                            currentState.copy(
                                categoriesError = "Could not load Live TV categories.",
                                categoryVisibilityReady = false,
                                channels = rawChannels
                            )
                        }
                    } else {
                        _uiState.update { currentState ->
                            currentState.copy(
                                categoriesError = "Could not load Live TV categories."
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadCategories(providerId: String, force: Boolean = false) {
        if (force) {
            categoriesLoadJob?.cancel()
        } else if (categoriesLoadJob?.isActive == true) {
            return
        }

        categoriesGeneration++
        val requestGeneration = categoriesGeneration

        val hasCategories = _uiState.value.categories.isNotEmpty()
        _uiState.update { currentState ->
            currentState.copy(
                categoriesLoading = !hasCategories,
                categoriesRefreshing = hasCategories,
                categoriesError = null
            )
        }

        categoriesLoadJob = viewModelScope.launch {
            try {
                dataSource.loadCategories(providerId).collect { categories ->
                    if (categoriesGeneration != requestGeneration || currentProviderId != providerId) return@collect

                    _uiState.update { current ->
                        if (current.categoryVisibilityReady) {
                            current.copy(
                                hasLoadedCategories = true
                            )
                        } else {
                            current.copy(
                                categories = categories,
                                hasLoadedCategories = true
                            )
                        }
                    }
                }
                if (categoriesGeneration == requestGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            categoriesLoading = false,
                            categoriesRefreshing = false,
                            hasLoadedCategories = true
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (categoriesGeneration == requestGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            categoriesError = "Could not load Live TV categories."
                        )
                    }
                }
            } finally {
                if (categoriesGeneration == requestGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            categoriesLoading = false,
                            categoriesRefreshing = false
                        )
                    }
                }
            }
        }
    }

    fun loadChannels(providerId: String, categoryId: String?, force: Boolean = false) {
        val normalizedCategoryId = if (categoryId.isNullOrBlank()) null else categoryId
        val requestKey = ChannelRequestKey(providerId, normalizedCategoryId)

        val previousRequestKey = activeChannelRequestKey
        val sameRequest = previousRequestKey == requestKey

        if (sameRequest && channelsLoadJob?.isActive == true && !force) {
            return
        }

        if (!sameRequest || force) {
            channelsLoadJob?.cancel()
        }

        channelsGeneration++
        val requestGeneration = channelsGeneration
        activeChannelRequestKey = requestKey

        val preserveExisting = sameRequest && rawChannels.isNotEmpty()

        if (!preserveExisting) {
            rawChannels = emptyList()
            _uiState.update { currentState ->
                currentState.copy(
                    channels = emptyList(),
                    hasLoadedChannels = false,
                    channelsError = null
                )
            }
        } else {
            _uiState.update { currentState ->
                currentState.copy(
                    channelsError = null
                )
            }
        }

        val hasChannels = rawChannels.isNotEmpty()
        _uiState.update { currentState ->
            currentState.copy(
                channelsLoading = !hasChannels,
                initialLoading = !hasChannels,
                channelsRefreshing = hasChannels
            )
        }

        channelsLoadJob = viewModelScope.launch {
            try {
                dataSource.loadChannels(providerId, normalizedCategoryId).collect { channels ->
                    if (channelsGeneration != requestGeneration || currentProviderId != providerId) return@collect

                    rawChannels = channels
                    _uiState.update { currentState ->
                        currentState.copy(
                            channels = applyVisibleCategoryFilter(channels, currentState),
                            hasLoadedChannels = true,
                            channelsLoading = false,
                            initialLoading = false,
                            channelsRefreshing = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (channelsGeneration == requestGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            channelsError = "Could not load Live TV channels."
                        )
                    }
                }
            } finally {
                if (channelsGeneration == requestGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            channelsLoading = false,
                            initialLoading = false,
                            channelsRefreshing = false
                        )
                    }
                }
            }
        }
    }

    fun selectCategory(categoryId: String?) {
        val normalized = if (categoryId.isNullOrBlank()) null else categoryId
        if (_uiState.value.selectedCategoryId == normalized) return

        _uiState.update { it.copy(selectedCategoryId = normalized) }

        currentProviderId?.let { providerId ->
            loadChannels(providerId, normalized)
        }
    }

    fun retryCategories() {
        val providerId = currentProviderId ?: return
        _uiState.update { it.copy(categoriesError = null) }
        loadCategories(providerId, force = true)
    }

    fun retryChannels() {
        val providerId = currentProviderId ?: return
        _uiState.update { it.copy(channelsError = null) }
        loadChannels(providerId, _uiState.value.selectedCategoryId, force = true)
    }

    private fun cancelCategoriesLoad(clearLoading: Boolean) {
        categoriesGeneration++
        categoriesLoadJob?.cancel()
        if (clearLoading) {
            _uiState.update { currentState ->
                currentState.copy(
                    categoriesLoading = false,
                    categoriesRefreshing = false
                )
            }
        }
    }

    private fun cancelChannelsLoad(clearLoading: Boolean) {
        channelsGeneration++
        channelsLoadJob?.cancel()
        activeChannelRequestKey = null
        if (clearLoading) {
            _uiState.update { currentState ->
                currentState.copy(
                    channelsLoading = false,
                    initialLoading = false,
                    channelsRefreshing = false
                )
            }
        }
    }

    private fun cancelVisibleCategoriesObserver() {
        visibleCategoriesJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        cancelVisibleCategoriesObserver()
        cancelCategoriesLoad(clearLoading = true)
        cancelChannelsLoad(clearLoading = true)
        _uiState.update { it.copy(categoryVisibilityReady = false) }
    }
}
