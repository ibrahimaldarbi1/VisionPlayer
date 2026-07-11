package com.example.ui.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.ProviderProfile
import com.example.data.Category
import com.example.data.LiveChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class LiveViewModel(
    private val dataSource: LiveDataSource,
    private val favoritesDataSource: LiveFavoritesDataSource,
    private val parentalDataSource: LiveParentalDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private var visibleCategoriesJob: Job? = null
    private var categoriesLoadJob: Job? = null
    private var channelsLoadJob: Job? = null
    private var favoritesObserverJob: Job? = null
    private val favoriteMutationJobs = mutableMapOf<String, Job>()

    private var currentProviderId: String? = null

    private var categoriesGeneration: Long = 0L
    private var channelsGeneration: Long = 0L
    private var favoritesGeneration: Long = 0L

    private var parentalObserverJob: Job? = null
    private var pinVerificationJob: Job? = null
    private var parentalGeneration: Long = 0L
    private var pinVerificationGeneration: Long = 0L
    private var validPinEnteredForCurrentGeneration = false
    private var isLiveVisible = false

    private val _events = MutableSharedFlow<LiveEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    private var persistedFavoriteChannelIds: Set<String> = emptySet()

    private data class PendingFavoriteMutation(
        val channelId: String,
        val desiredFavorite: Boolean,
        val providerId: String,
        val generation: Long
    )

    private val pendingFavoriteMutations = mutableMapOf<String, PendingFavoriteMutation>()

    private fun mergedFavoriteChannelIds(): Set<String> {
        var result = persistedFavoriteChannelIds

        pendingFavoriteMutations.values
            .filter {
                it.providerId == currentProviderId &&
                it.generation == favoritesGeneration
            }
            .forEach { mutation ->
                result = if (mutation.desiredFavorite) {
                    result + mutation.channelId
                } else {
                    result - mutation.channelId
                }
            }

        return result
    }

    private fun removePendingMutationIfCurrent(
        mutation: PendingFavoriteMutation
    ): Boolean {
        val current = pendingFavoriteMutations[mutation.channelId]
        if (current == mutation) {
            pendingFavoriteMutations.remove(mutation.channelId)
            return true
        }
        return false
    }

    private data class ChannelRequestKey(
        val providerId: String,
        val categoryId: String?
    )

    private var activeChannelRequestKey: ChannelRequestKey? = null
    private var rawVisibleCategories: List<Category> = emptyList()
    private var rawChannels: List<LiveChannel> = emptyList()

    private fun applyParentalCategoryPolicy(
        categories: List<Category>,
        state: LiveUiState = _uiState.value
    ): List<Category> {
        if (!state.parentalControlsEnabled || !state.hideAdultContent) {
            return categories
        }

        return categories.filterNot {
            it.id in state.lockedLiveCategoryIds
        }
    }

    private fun applyLiveChannelPolicy(
        channels: List<LiveChannel>,
        state: LiveUiState = _uiState.value
    ): List<LiveChannel> {
        var result = channels

        if (state.categoryVisibilityReady) {
            val visibleCategoryIds = rawVisibleCategories
                .map { it.id }
                .toSet()

            result = result.filter {
                it.categoryId in visibleCategoryIds
            }
        }

        if (state.parentalControlsEnabled && state.hideAdultContent) {
            result = result.filterNot { channel ->
                channel.isAdult || channel.categoryId in state.lockedLiveCategoryIds
            }
        }

        return result
    }

    private fun checkSelectedCategoryValidity() {
        val state = _uiState.value
        val selectedCategoryId = state.selectedCategoryId ?: return
        val providerId = currentProviderId ?: return

        val displayedCategoryIds = state.categories.map { it.id }.toSet()
        val isInvalid = selectedCategoryId !in displayedCategoryIds

        if (isInvalid) {
            cancelPinVerificationIfForCategory(selectedCategoryId)
            _uiState.update { current ->
                val pendingCategoryCleared = if (current.pendingParentalCategoryId == selectedCategoryId) null else current.pendingParentalCategoryId
                current.copy(
                    selectedCategoryId = null,
                    pendingParentalCategoryId = pendingCategoryCleared
                )
            }
            loadChannels(providerId, null)
        }
    }

    private fun cancelPinVerificationIfForCategory(categoryId: String) {
        val state = _uiState.value
        if (state.pendingParentalCategoryId == categoryId) {
            cancelPinVerification(invalidate = true, clearLoading = true)
        }
    }

    fun onProfileChanged(profile: ProviderProfile) {
        val favoritesEnabled = profile.features.favoritesEnabled
        val parentalEnabled = profile.features.parentalControlEnabled

        if (!profile.features.liveTvEnabled) {
            cancelVisibleCategoriesObserver()
            cancelCategoriesLoad(clearLoading = true)
            cancelChannelsLoad(clearLoading = true)
            cancelFavoritesObserver(clearLoading = true)
            cancelFavoriteMutations(clearState = true)
            cancelParentalObserver(invalidate = true, clearLoading = true)
            cancelPinVerification(invalidate = true, clearLoading = true)
            validPinEnteredForCurrentGeneration = false
            rawChannels = emptyList()
            currentProviderId = null
            persistedFavoriteChannelIds = emptySet()
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
                    categoryVisibilityReady = false,
                    favoritesEnabled = false,
                    favoriteChannelIds = emptySet(),
                    favoriteMutationChannelIds = emptySet(),
                    favoritesLoading = false,
                    favoritesLoadError = null,
                    favoriteMutationError = null,

                    parentalControlsEnabled = false,
                    parentalReady = false,
                    parentalPinConfigured = false,
                    parentalLoading = false,
                    parentalLoadError = null,
                    parentalSessionUnlocked = false,
                    pinDialogVisible = false,
                    pendingParentalChannel = null,
                    pinVerificationLoading = false,
                    pinVerificationError = null
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

            // Provider changed favorite cleanup
            cancelFavoritesObserver(clearLoading = true)
            cancelFavoriteMutations(clearState = true)
            persistedFavoriteChannelIds = emptySet()

            // Clear parental controls on provider change / wake up
            cancelParentalObserver(invalidate = true, clearLoading = true)
            cancelPinVerification(invalidate = true, clearLoading = true)
            validPinEnteredForCurrentGeneration = false

            // Clear old provider channels and categories
            rawVisibleCategories = emptyList()
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
                    categoryVisibilityReady = false,
                    favoritesEnabled = favoritesEnabled,
                    favoriteChannelIds = emptySet(),
                    favoriteMutationChannelIds = emptySet(),
                    favoritesLoadError = null,
                    favoriteMutationError = null,

                    parentalControlsEnabled = parentalEnabled,
                    parentalReady = false,
                    parentalPinConfigured = false,
                    parentalLoading = false,
                    parentalLoadError = null,
                    parentalSessionUnlocked = false,
                    pinDialogVisible = false,
                    pendingParentalChannel = null,
                    pinVerificationLoading = false,
                    pinVerificationError = null,
                    lockedLiveCategoryIds = emptySet(),
                    hideAdultContent = false,
                    pendingParentalCategoryId = null
                )
            }

            currentProviderId = profile.providerId

            // Start visible-category observation
            observeCategoryVisibility(profile.providerId)

            // Load/seed categories
            loadCategories(profile.providerId)

            // Load channels for the selected category, initially null
            loadChannels(profile.providerId, null)

            if (favoritesEnabled) {
                observeLiveFavorites(profile.providerId)
            }

            if (parentalEnabled) {
                observeParentalStatus(profile.providerId)
            }
        } else {
            val wasFavoritesEnabled = _uiState.value.favoritesEnabled
            if (favoritesEnabled != wasFavoritesEnabled) {
                _uiState.update { currentState ->
                    currentState.copy(
                        favoritesEnabled = favoritesEnabled
                    )
                }
                if (favoritesEnabled) {
                    currentProviderId?.let { providerId ->
                        observeLiveFavorites(providerId)
                    }
                } else {
                    cancelFavoritesObserver(clearLoading = true)
                    cancelFavoriteMutations(clearState = true)
                    persistedFavoriteChannelIds = emptySet()
                    _uiState.update { currentState ->
                        currentState.copy(
                            favoriteChannelIds = emptySet(),
                            favoriteMutationChannelIds = emptySet(),
                            favoritesLoadError = null,
                            favoriteMutationError = null
                        )
                    }
                }
            }

            val wasParentalEnabled = _uiState.value.parentalControlsEnabled
            if (parentalEnabled != wasParentalEnabled) {
                if (parentalEnabled) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            parentalControlsEnabled = true,
                            parentalReady = false,
                            parentalPinConfigured = false,
                            parentalLoading = false,
                            parentalLoadError = null,
                            parentalSessionUnlocked = false,
                            pinDialogVisible = false,
                            pendingParentalChannel = null,
                            pinVerificationLoading = false,
                            pinVerificationError = null
                        )
                    }
                    currentProviderId?.let { providerId ->
                        observeParentalStatus(providerId)
                    }
                } else {
                    cancelParentalObserver(invalidate = true, clearLoading = true)
                    cancelPinVerification(invalidate = true, clearLoading = true)
                    validPinEnteredForCurrentGeneration = false
                    _uiState.update { currentState ->
                        val baseState = currentState.copy(
                            parentalControlsEnabled = false,
                            parentalReady = false,
                            parentalPinConfigured = false,
                            parentalLoading = false,
                            parentalLoadError = null,
                            parentalSessionUnlocked = false,
                            pinDialogVisible = false,
                            pendingParentalChannel = null,
                            pinVerificationLoading = false,
                            pinVerificationError = null,
                            lockedLiveCategoryIds = emptySet(),
                            hideAdultContent = false,
                            pendingParentalCategoryId = null
                        )
                        baseState.copy(
                            categories = applyParentalCategoryPolicy(rawVisibleCategories, baseState),
                            channels = applyLiveChannelPolicy(rawChannels, baseState)
                        )
                    }
                }
            }
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
                                channels = applyLiveChannelPolicy(rawChannels, current)
                            )
                        }
                    } else {
                        val categories = snapshot.visibleCategories
                        rawVisibleCategories = categories

                        _uiState.update { current ->
                            val updated = current.copy(
                                categories = applyParentalCategoryPolicy(rawVisibleCategories, current),
                                categoryVisibilityReady = true,
                                categoriesError = null
                            )
                            updated.copy(
                                channels = applyLiveChannelPolicy(rawChannels, updated)
                            )
                        }

                        checkSelectedCategoryValidity()
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
                            rawVisibleCategories = categories
                            current.copy(
                                categories = applyParentalCategoryPolicy(rawVisibleCategories, current),
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
                            channels = applyLiveChannelPolicy(channels, currentState),
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

    private fun applyCategorySelection(categoryId: String?) {
        val normalized = if (categoryId.isNullOrBlank()) null else categoryId
        if (_uiState.value.selectedCategoryId == normalized) {
            _uiState.update { it.copy(pendingParentalCategoryId = null) }
            return
        }

        _uiState.update {
            it.copy(
                selectedCategoryId = normalized,
                pendingParentalCategoryId = null
            )
        }

        currentProviderId?.let { providerId ->
            loadChannels(providerId, normalized)
        }
    }

    fun selectCategory(categoryId: String?) {
        val normalizedCategoryId = if (categoryId.isNullOrBlank()) null else categoryId
        if (normalizedCategoryId == null) {
            applyCategorySelection(null)
            return
        }

        val state = _uiState.value
        val isLocked = normalizedCategoryId in state.lockedLiveCategoryIds

        if (!state.parentalControlsEnabled || !isLocked) {
            applyCategorySelection(normalizedCategoryId)
            return
        }

        if (state.hideAdultContent) {
            // Ignore the selection, do not load, do not show dialog
            return
        }

        if (state.parentalSessionUnlocked) {
            applyCategorySelection(normalizedCategoryId)
            return
        }

        if (!state.parentalReady) {
            _uiState.update {
                it.copy(
                    pendingParentalCategoryId = normalizedCategoryId,
                    pendingParentalChannel = null
                )
            }
            return
        }

        if (state.parentalPinConfigured) {
            _uiState.update {
                it.copy(
                    pendingParentalCategoryId = normalizedCategoryId,
                    pendingParentalChannel = null,
                    pinDialogVisible = true,
                    pinVerificationError = null
                )
            }
        } else {
            applyCategorySelection(normalizedCategoryId)
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

    private fun observeLiveFavorites(providerId: String) {
        favoritesObserverJob?.cancel()
        favoritesGeneration++
        val requestGeneration = favoritesGeneration

        _uiState.update { currentState ->
            currentState.copy(
                favoritesLoading = true,
                favoritesLoadError = null
            )
        }

        favoritesObserverJob = viewModelScope.launch {
            try {
                favoritesDataSource.observeLiveFavorites(providerId).collect { favorites ->
                    if (favoritesGeneration != requestGeneration || currentProviderId != providerId || !_uiState.value.favoritesEnabled) {
                        return@collect
                    }

                    persistedFavoriteChannelIds = favorites.map { it.contentId }.toSet()
                    _uiState.update { currentState ->
                        currentState.copy(
                            favoriteChannelIds = mergedFavoriteChannelIds(),
                            favoritesLoading = false,
                            favoritesLoadError = null
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (favoritesGeneration == requestGeneration && currentProviderId == providerId && _uiState.value.favoritesEnabled) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            favoritesLoadError = "Could not load Live TV favorites.",
                            favoritesLoading = false
                        )
                    }
                }
            }
        }
    }

    fun toggleFavorite(channel: LiveChannel) {
        val providerId = currentProviderId
        if (!_uiState.value.featureEnabled || !_uiState.value.favoritesEnabled || providerId == null) {
            return
        }
        if (pendingFavoriteMutations.containsKey(channel.id)) {
            return
        }

        val wasFavorite = channel.id in mergedFavoriteChannelIds()
        val desiredFavorite = !wasFavorite
        val requestGeneration = favoritesGeneration

        val mutation = PendingFavoriteMutation(
            channelId = channel.id,
            desiredFavorite = desiredFavorite,
            providerId = providerId,
            generation = requestGeneration
        )

        pendingFavoriteMutations[channel.id] = mutation

        _uiState.update { currentState ->
            currentState.copy(
                favoriteChannelIds = mergedFavoriteChannelIds(),
                favoriteMutationChannelIds = pendingFavoriteMutations.keys.toSet()
            )
        }

        lateinit var mutationJob: Job
        mutationJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (wasFavorite) {
                    favoritesDataSource.removeLiveFavorite(channel.id)
                } else {
                    favoritesDataSource.addLiveFavorite(channel)
                }

                if (favoritesGeneration == requestGeneration && currentProviderId == providerId) {
                    persistedFavoriteChannelIds = if (desiredFavorite) {
                        persistedFavoriteChannelIds + channel.id
                    } else {
                        persistedFavoriteChannelIds - channel.id
                    }
                    _uiState.update { currentState ->
                        currentState.copy(
                            favoriteMutationError = null
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (favoritesGeneration == requestGeneration && currentProviderId == providerId) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            favoriteMutationError = "Could not update this favorite."
                        )
                    }
                }
            } finally {
                val isCurrent = favoritesGeneration == requestGeneration && currentProviderId == providerId
                val removed = if (isCurrent) removePendingMutationIfCurrent(mutation) else false
                if (removed) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            favoriteChannelIds = mergedFavoriteChannelIds(),
                            favoriteMutationChannelIds = pendingFavoriteMutations.keys.toSet()
                        )
                    }
                }
                if (favoriteMutationJobs[channel.id] === mutationJob) {
                    favoriteMutationJobs.remove(channel.id)
                }
            }
        }

        favoriteMutationJobs[channel.id] = mutationJob
        mutationJob.start()
    }

    private fun cancelFavoriteMutations(clearState: Boolean) {
        favoritesGeneration++
        val jobsToCancel = favoriteMutationJobs.values.toList()
        favoriteMutationJobs.clear()
        pendingFavoriteMutations.clear()
        jobsToCancel.forEach { it.cancel() }
        if (clearState) {
            _uiState.update { currentState ->
                currentState.copy(
                    favoriteChannelIds = if (currentState.favoritesEnabled && currentProviderId != null) mergedFavoriteChannelIds() else emptySet(),
                    favoriteMutationChannelIds = emptySet()
                )
            }
        }
    }

    private fun cancelFavoritesObserver(clearLoading: Boolean) {
        favoritesObserverJob?.cancel()
        if (clearLoading) {
            _uiState.update { currentState ->
                currentState.copy(
                    favoritesLoading = false
                )
            }
        }
    }

    fun dismissFavoritesError() {
        _uiState.update { currentState ->
            currentState.copy(
                favoritesLoadError = null,
                favoriteMutationError = null
            )
        }
    }

    private fun cancelParentalObserver(
        invalidate: Boolean,
        clearLoading: Boolean
    ) {
        if (invalidate) {
            parentalGeneration++
        }
        val jobToCancel = parentalObserverJob
        parentalObserverJob = null
        jobToCancel?.cancel()
        if (clearLoading) {
            _uiState.update { it.copy(parentalLoading = false) }
        }
    }

    private fun cancelPinVerification(
        invalidate: Boolean,
        clearLoading: Boolean
    ) {
        if (invalidate) {
            pinVerificationGeneration++
        }
        val jobToCancel = pinVerificationJob
        pinVerificationJob = null
        jobToCancel?.cancel()
        if (clearLoading) {
            _uiState.update { it.copy(pinVerificationLoading = false) }
        }
    }

    private fun emitPlayChannel(channel: LiveChannel): Boolean {
        if (!_uiState.value.featureEnabled || !isLiveVisible) {
            return false
        }
        return _events.tryEmit(LiveEvent.PlayChannel(channel))
    }

    private fun observeParentalStatus(providerId: String) {
        cancelParentalObserver(invalidate = true, clearLoading = true)
        val requestGeneration = parentalGeneration
        validPinEnteredForCurrentGeneration = false

        _uiState.update { currentState ->
            currentState.copy(
                parentalLoading = true,
                parentalLoadError = null
            )
        }

        lateinit var observerJob: Job

        observerJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                parentalDataSource.observeStatus(providerId).collect { status ->
                    if (parentalGeneration != requestGeneration ||
                        currentProviderId != providerId ||
                        !_uiState.value.parentalControlsEnabled
                    ) {
                        return@collect
                    }

                    val pinConfigured = status.pinConfigured
                    val lockedCategoryIds = status.lockedCategoryIds
                    val hideAdultContent = status.hideAdultContent

                    _uiState.update { currentState ->
                        val baseState = if (!pinConfigured) {
                            currentState.copy(
                                parentalReady = true,
                                parentalPinConfigured = false,
                                parentalSessionUnlocked = true,
                                pinDialogVisible = false,
                                parentalLoading = false,
                                parentalLoadError = null,
                                lockedLiveCategoryIds = lockedCategoryIds,
                                hideAdultContent = hideAdultContent
                            )
                        } else {
                            val unlocked = validPinEnteredForCurrentGeneration
                            currentState.copy(
                                parentalReady = true,
                                parentalPinConfigured = true,
                                parentalSessionUnlocked = unlocked,
                                parentalLoading = false,
                                parentalLoadError = null,
                                lockedLiveCategoryIds = lockedCategoryIds,
                                hideAdultContent = hideAdultContent
                            )
                        }

                        baseState.copy(
                            categories = applyParentalCategoryPolicy(rawVisibleCategories, baseState),
                            channels = applyLiveChannelPolicy(rawChannels, baseState)
                        )
                    }

                    checkSelectedCategoryValidity()

                    // Handle pending actions after status emission
                    val state = _uiState.value
                    val pendingChannel = state.pendingParentalChannel
                    val pendingCategoryId = state.pendingParentalCategoryId

                    if (hideAdultContent) {
                        val isPendingChannelHidden = pendingChannel != null && (pendingChannel.isAdult || pendingChannel.categoryId in lockedCategoryIds)
                        val isPendingCategoryHidden = pendingCategoryId != null && pendingCategoryId in lockedCategoryIds

                        if (isPendingChannelHidden || isPendingCategoryHidden) {
                            cancelPinVerification(invalidate = true, clearLoading = true)
                            _uiState.update { it.copy(
                                pinDialogVisible = false,
                                pendingParentalChannel = null,
                                pendingParentalCategoryId = null
                            ) }
                            return@collect
                        }
                    }

                    if (pendingChannel != null) {
                        if (!pinConfigured) {
                            if (parentalGeneration == requestGeneration && currentProviderId == providerId) {
                                if (isLiveVisible) {
                                    emitPlayChannel(pendingChannel)
                                }
                                _uiState.update { it.copy(pendingParentalChannel = null) }
                            }
                        } else {
                            if (isLiveVisible) {
                                _uiState.update { it.copy(pinDialogVisible = true, pinVerificationError = null) }
                            } else {
                                _uiState.update { it.copy(pendingParentalChannel = null) }
                            }
                        }
                    } else if (pendingCategoryId != null) {
                        if (!pinConfigured) {
                            if (parentalGeneration == requestGeneration && currentProviderId == providerId) {
                                if (isLiveVisible) {
                                    applyCategorySelection(pendingCategoryId)
                                }
                                _uiState.update { it.copy(pendingParentalCategoryId = null) }
                            }
                        } else {
                            if (isLiveVisible) {
                                _uiState.update { it.copy(pinDialogVisible = true, pinVerificationError = null) }
                            } else {
                                _uiState.update { it.copy(pendingParentalCategoryId = null) }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (parentalGeneration == requestGeneration &&
                    currentProviderId == providerId &&
                    _uiState.value.parentalControlsEnabled
                ) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            parentalLoading = false,
                            parentalLoadError = "Could not load parental-control settings."
                        )
                    }
                }
            } finally {
                if (parentalObserverJob === observerJob) {
                    parentalObserverJob = null
                }
            }
        }

        parentalObserverJob = observerJob
        observerJob.start()
    }

    private fun requiresParentalUnlock(
        channel: LiveChannel,
        state: LiveUiState = _uiState.value
    ): Boolean {
        return channel.isAdult ||
                channel.isLocked ||
                channel.categoryId in state.lockedLiveCategoryIds
    }

    fun onChannelSelected(channel: LiveChannel) {
        val state = _uiState.value
        if (!state.featureEnabled || !isLiveVisible) return

        if (state.parentalControlsEnabled && state.hideAdultContent && (channel.isAdult || channel.categoryId in state.lockedLiveCategoryIds)) {
            return
        }

        if (!requiresParentalUnlock(channel, state)) {
            emitPlayChannel(channel)
            return
        }

        if (!state.parentalControlsEnabled) {
            emitPlayChannel(channel)
            return
        }

        if (state.parentalSessionUnlocked) {
            emitPlayChannel(channel)
            return
        }

        if (state.pinVerificationLoading) return

        if (!state.parentalReady) {
            _uiState.update {
                it.copy(
                    pendingParentalChannel = channel,
                    pendingParentalCategoryId = null
                )
            }
            return
        }

        if (state.parentalPinConfigured) {
            _uiState.update { currentState ->
                currentState.copy(
                    pendingParentalChannel = channel,
                    pendingParentalCategoryId = null,
                    pinDialogVisible = true,
                    pinVerificationError = null
                )
            }
        } else {
            emitPlayChannel(channel)
        }
    }

    fun submitParentalPin(candidatePin: String) {
        if (candidatePin.length != 4 || !candidatePin.all { it.isDigit() }) {
            _uiState.update { it.copy(pinVerificationError = "Enter a 4-digit PIN.") }
            return
        }

        val state = _uiState.value
        val providerId = currentProviderId
        val pendingChannel = state.pendingParentalChannel
        val pendingCategoryId = state.pendingParentalCategoryId

        // Require exactly one pending action
        val hasPendingChannel = pendingChannel != null
        val hasPendingCategory = pendingCategoryId != null
        if (!(hasPendingChannel xor hasPendingCategory)) {
            return
        }

        if (!state.parentalControlsEnabled ||
            providerId == null ||
            !state.pinDialogVisible ||
            !state.parentalReady ||
            !state.parentalPinConfigured ||
            state.pinVerificationLoading
        ) {
            return
        }

        cancelPinVerification(invalidate = true, clearLoading = true)
        val requestPinGen = pinVerificationGeneration
        val requestParentalGen = parentalGeneration
        val requestProviderId = providerId
        val requestPendingChannel = pendingChannel
        val requestPendingCategoryId = pendingCategoryId

        _uiState.update { currentState ->
            currentState.copy(
                pinVerificationLoading = true,
                pinVerificationError = null
            )
        }

        lateinit var verificationJob: Job
        verificationJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val isValid = parentalDataSource.verifyPin(requestProviderId, candidatePin)

                if (parentalGeneration != requestParentalGen ||
                    pinVerificationGeneration != requestPinGen ||
                    currentProviderId != requestProviderId ||
                    !_uiState.value.parentalControlsEnabled ||
                    !_uiState.value.pinDialogVisible ||
                    _uiState.value.pendingParentalChannel != requestPendingChannel ||
                    _uiState.value.pendingParentalCategoryId != requestPendingCategoryId
                ) {
                    return@launch
                }

                if (isValid) {
                    if (!isLiveVisible) {
                        return@launch
                    }
                    validPinEnteredForCurrentGeneration = true
                    _uiState.update { currentState ->
                        currentState.copy(
                            parentalSessionUnlocked = true,
                            pinDialogVisible = false,
                            pendingParentalChannel = null,
                            pendingParentalCategoryId = null,
                            pinVerificationLoading = false,
                            pinVerificationError = null
                        )
                    }
                    if (requestPendingChannel != null) {
                        emitPlayChannel(requestPendingChannel)
                    } else if (requestPendingCategoryId != null) {
                        applyCategorySelection(requestPendingCategoryId)
                    }
                } else {
                    _uiState.update { currentState ->
                        currentState.copy(
                            pinVerificationLoading = false,
                            pinVerificationError = "Incorrect PIN. Please try again."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (parentalGeneration == requestParentalGen &&
                    pinVerificationGeneration == requestPinGen &&
                    currentProviderId == requestProviderId &&
                    _uiState.value.parentalControlsEnabled &&
                    _uiState.value.pinDialogVisible &&
                    _uiState.value.pendingParentalChannel == requestPendingChannel &&
                    _uiState.value.pendingParentalCategoryId == requestPendingCategoryId
                ) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            pinVerificationLoading = false,
                            pinVerificationError = "Could not verify the PIN."
                        )
                    }
                }
            } finally {
                if (pinVerificationJob === verificationJob) {
                    pinVerificationJob = null
                }
            }
        }

        pinVerificationJob = verificationJob
        verificationJob.start()
    }

    fun cancelParentalDialog() {
        cancelPinVerification(invalidate = true, clearLoading = true)
        _uiState.update { currentState ->
            currentState.copy(
                pinDialogVisible = false,
                pendingParentalChannel = null,
                pendingParentalCategoryId = null,
                pinVerificationLoading = false,
                pinVerificationError = null
            )
        }
    }

    fun retryParentalStatus() {
        val providerId = currentProviderId ?: return
        _uiState.update { currentState ->
            currentState.copy(
                parentalLoadError = null
            )
        }
        observeParentalStatus(providerId)
    }

    fun dismissParentalLoadError() {
        _uiState.update { currentState ->
            currentState.copy(
                parentalLoadError = null
            )
        }
    }

    fun onLiveVisibilityChanged(visible: Boolean) {
        isLiveVisible = visible
        if (!visible) {
            cancelPinVerification(invalidate = true, clearLoading = true)
            validPinEnteredForCurrentGeneration = false

            val state = _uiState.value
            val shouldResetCategory = state.parentalControlsEnabled &&
                    state.parentalPinConfigured &&
                    state.selectedCategoryId != null &&
                    state.selectedCategoryId in state.lockedLiveCategoryIds

            _uiState.update { currentState ->
                currentState.copy(
                    pinDialogVisible = false,
                    pendingParentalChannel = null,
                    pendingParentalCategoryId = null,
                    pinVerificationLoading = false,
                    pinVerificationError = null,
                    parentalSessionUnlocked = if (currentState.parentalPinConfigured) false else currentState.parentalSessionUnlocked,
                    selectedCategoryId = if (shouldResetCategory) null else currentState.selectedCategoryId
                )
            }

            if (shouldResetCategory) {
                currentProviderId?.let { providerId ->
                    loadChannels(providerId, null)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelVisibleCategoriesObserver()
        cancelCategoriesLoad(clearLoading = true)
        cancelChannelsLoad(clearLoading = true)
        cancelFavoritesObserver(clearLoading = true)
        cancelFavoriteMutations(clearState = true)
        cancelParentalObserver(invalidate = true, clearLoading = true)
        cancelPinVerification(invalidate = true, clearLoading = true)
        rawVisibleCategories = emptyList()
        _uiState.update { it.copy(categoryVisibilityReady = false) }
    }
}
