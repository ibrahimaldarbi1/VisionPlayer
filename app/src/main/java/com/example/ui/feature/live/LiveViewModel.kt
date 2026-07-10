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
                    refreshing = false,
                    categoriesLoading = false,
                    channelsLoading = false
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
                    refreshing = false,
                    categoriesLoading = false,
                    channelsLoading = false
                )
            }

            currentProviderId = profile.providerId

            // Start visible-category observation
            observeVisibleCategories(profile.providerId)

            // Load/seed categories
            loadCategories(profile.providerId)

            // Load channels for the selected category, initially null
            loadChannels(profile.providerId, null)
        }
    }

    private fun observeVisibleCategories(providerId: String) {
        visibleCategoriesJob?.cancel()
        visibleCategoriesJob = viewModelScope.launch {
            try {
                dataSource.observeVisibleCategories(providerId).collect { categories ->
                    if (currentProviderId != providerId) return@collect

                    val visibleIds = categories.map { it.id }.toSet()
                    val selectedId = _uiState.value.selectedCategoryId
                    val isSelectedCategoryVisible = selectedId == null || visibleIds.contains(selectedId)

                    _uiState.update { currentState ->
                        currentState.copy(
                            categories = categories,
                            channels = rawChannels.filter { it.categoryId in visibleIds }
                        )
                    }

                    if (!isSelectedCategoryVisible) {
                        _uiState.update { it.copy(selectedCategoryId = null) }
                        loadChannels(providerId, null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            categoriesError = "Could not load Live TV categories."
                        )
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
        val myGeneration = categoriesGeneration

        val hasCategories = _uiState.value.categories.isNotEmpty()
        _uiState.update { currentState ->
            currentState.copy(
                categoriesLoading = !hasCategories,
                refreshing = hasCategories && currentState.channelsLoading, // If channels are already loading
                categoriesError = null
            )
        }

        categoriesLoadJob = viewModelScope.launch {
            try {
                dataSource.loadCategories(providerId).collect { categories ->
                    if (categoriesGeneration != myGeneration || currentProviderId != providerId) return@collect

                    _uiState.update { currentState ->
                        currentState.copy(
                            hasLoadedCategories = true
                        )
                    }
                }
                if (categoriesGeneration == myGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            categoriesLoading = false,
                            hasLoadedCategories = true
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (categoriesGeneration == myGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            categoriesError = "Could not load Live TV categories."
                        )
                    }
                }
            } finally {
                if (categoriesGeneration == myGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            categoriesLoading = false
                        )
                    }
                }
            }
        }
    }

    fun loadChannels(providerId: String, categoryId: String?, force: Boolean = false) {
        val normalizedCategoryId = if (categoryId.isNullOrBlank()) null else categoryId
        val requestKey = ChannelRequestKey(providerId, normalizedCategoryId)

        if (activeChannelRequestKey == requestKey && channelsLoadJob?.isActive == true && !force) {
            return
        }

        if (activeChannelRequestKey != requestKey) {
            channelsLoadJob?.cancel()
        } else if (force) {
            channelsLoadJob?.cancel()
        }

        channelsGeneration++
        val myGeneration = channelsGeneration
        activeChannelRequestKey = requestKey

        val isSameRequestKey = activeChannelRequestKey == requestKey
        if (!isSameRequestKey || !force) {
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
                refreshing = hasChannels
            )
        }

        channelsLoadJob = viewModelScope.launch {
            try {
                dataSource.loadChannels(providerId, normalizedCategoryId).collect { channels ->
                    if (channelsGeneration != myGeneration || currentProviderId != providerId) return@collect

                    rawChannels = channels
                    val visibleIds = _uiState.value.categories.map { it.id }.toSet()
                    _uiState.update { currentState ->
                        currentState.copy(
                            channels = channels.filter { it.categoryId in visibleIds },
                            hasLoadedChannels = true,
                            channelsLoading = false,
                            initialLoading = false,
                            refreshing = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (channelsGeneration == myGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            channelsError = "Could not load Live TV channels."
                        )
                    }
                }
            } finally {
                if (channelsGeneration == myGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            channelsLoading = false,
                            initialLoading = false,
                            refreshing = false
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
                    refreshing = false
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
                    refreshing = false
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
    }
}
