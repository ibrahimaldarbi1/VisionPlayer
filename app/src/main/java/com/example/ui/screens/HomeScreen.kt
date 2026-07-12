package com.example.ui.screens

import com.example.config.ProviderProfile

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.ui.feature.shell.AppDestination
import com.example.ui.feature.shell.AppNavigationState
import com.example.ui.feature.shell.AppShell
import com.example.ui.feature.shell.FeatureAvailabilityPolicy
import com.example.ui.feature.shell.rememberAppNavigationState
import com.example.ui.feature.common.DeviceType
import com.example.ui.feature.common.rememberDeviceType

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.feature.common.*
import com.example.ui.feature.home.HomeDashboardView
import com.example.ui.feature.live.LiveChannelsView
import com.example.ui.feature.movies.MoviesLibraryView
import com.example.ui.feature.series.SeriesLibraryView
import com.example.ui.feature.epg.TvGuideView
import com.example.ui.feature.search.SearchPanel
import com.example.ui.feature.settings.SettingsView
import com.example.ui.feature.series.SeriesDetailsDialog
import com.example.ui.feature.multiview.MultiViewPlayerScreen
import com.example.ui.feature.multiview.MultiViewSetupView
import com.example.ui.feature.football.FootballConfigDialog
import com.example.ui.feature.football.FootballViewModel
import com.example.ui.feature.football.RepositoryFootballDataSource
import com.example.ui.feature.live.LiveViewModel
import com.example.ui.feature.live.RepositoryLiveDataSource
import com.example.ui.feature.live.RepositoryLiveFavoritesDataSource
import com.example.ui.feature.live.RepositoryLiveParentalDataSource
import com.example.ui.feature.live.LiveEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HomeScreen(
    profile: ProviderProfile,
    onProfileSelected: (ProviderProfile) -> Unit,
    repository: IptvRepository,
    addToMultiViewChannel: LiveChannel? = null,
    onAddToMultiViewHandled: () -> Unit = {},
    onPlayLive: (LiveChannel) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onPlayEpisode: (Series, Episode) -> Unit,
    onNavigateToSupport: (() -> Unit)?,
    onNavigateToParental: (() -> Unit)?,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val deviceType = rememberDeviceType()
    val isTv = deviceType == DeviceType.TV

    // Dynamic state triggered by active provider profile configurations
    val navigationState = rememberAppNavigationState()
    val coroutineScope = rememberCoroutineScope()

    // Home recommendations ViewModel state
    val homeViewModel: HomeViewModel = viewModel(
        factory = com.example.core.viewmodel.AppViewModelFactory {
            HomeViewModel(repository)
        }
    )
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val homeFavorites by homeViewModel.favorites.collectAsStateWithLifecycle()
    val homeContinueWatching by homeViewModel.continueWatching.collectAsStateWithLifecycle()
    val homeRecentlyWatched by homeViewModel.recentlyWatched.collectAsStateWithLifecycle()
    val unavailableTmdbItem by homeViewModel.unavailableTmdbItem.collectAsStateWithLifecycle()

    LaunchedEffect(profile) {
        homeViewModel.onProfileChanged(profile)
    }

    // Live ViewModel & States
    val liveViewModelFactory = remember(repository) {
        com.example.core.viewmodel.AppViewModelFactory {
            LiveViewModel(
                dataSource = RepositoryLiveDataSource(repository),
                favoritesDataSource = RepositoryLiveFavoritesDataSource(repository),
                parentalDataSource = RepositoryLiveParentalDataSource(repository)
            )
        }
    }
    val liveViewModel: LiveViewModel = viewModel(factory = liveViewModelFactory)
    val liveState by liveViewModel.uiState.collectAsStateWithLifecycle()

    val latestOnPlayLive by rememberUpdatedState(onPlayLive)

    LaunchedEffect(liveViewModel) {
        liveViewModel.events.collect { event ->
            when (event) {
                is LiveEvent.PlayChannel -> {
                    latestOnPlayLive(event.channel)
                }
            }
        }
    }

    LaunchedEffect(profile) {
        liveViewModel.onProfileChanged(profile)
    }

    LaunchedEffect(navigationState.activeDestination) {
        liveViewModel.onLiveVisibilityChanged(com.example.ui.feature.live.LiveContentSurfaceVisibilityPolicy.isVisible(navigationState.activeDestination))
    }

    DisposableEffect(liveViewModel) {
        onDispose {
            liveViewModel.onLiveVisibilityChanged(false)
        }
    }

    // Football ViewModel & States
    val footballViewModel: FootballViewModel = viewModel(
        factory = com.example.core.viewmodel.AppViewModelFactory {
            FootballViewModel(
                RepositoryFootballDataSource(repository)
            )
        }
    )
    val footballState by footballViewModel.uiState.collectAsStateWithLifecycle()

    // EPG ViewModel & States
    val epgViewModelFactory = remember(repository) {
        com.example.core.viewmodel.AppViewModelFactory {
            com.example.ui.feature.epg.EpgViewModel(
                dataSource = com.example.ui.feature.epg.RepositoryEpgDataSource(repository)
            )
        }
    }
    val epgViewModel: com.example.ui.feature.epg.EpgViewModel = viewModel(factory = epgViewModelFactory)
    val epgState by epgViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(profile, liveState.providerId, liveState.channels) {
        epgViewModel.onProfileChanged(profile)
        if (liveState.providerId == profile.providerId) {
            epgViewModel.onChannelsChanged(
                providerId = profile.providerId,
                channels = liveState.channels
            )
        }
    }

    LaunchedEffect(navigationState.activeDestination) {
        epgViewModel.onGuideVisibilityChanged(navigationState.activeDestination == AppDestination.EPG)
    }

    DisposableEffect(epgViewModel) {
        onDispose {
            epgViewModel.onGuideVisibilityChanged(false)
        }
    }

    // 1. Profile initialization/change
    LaunchedEffect(profile) {
        footballViewModel.onProfileChanged(profile)
    }

    // 2. Home visibility & disposal
    LaunchedEffect(navigationState.activeDestination, profile.providerId) {
        footballViewModel.onHomeVisibilityChanged(
            visible = navigationState.activeDestination == AppDestination.HOME,
            providerId = profile.providerId
        )
    }

    DisposableEffect(footballViewModel) {
        onDispose {
            footballViewModel.onHomeVisibilityChanged(
                visible = false,
                providerId = profile.providerId
            )
        }
    }

    // 3. Criteria change effect
    LaunchedEffect(
        footballState.selectedCompetitionKeys,
        footballState.showOnHome,
        profile.providerId
    ) {
        footballViewModel.onScheduleCriteriaChanged(
            providerId = profile.providerId
        )
    }

    // Movies ViewModel
    val moviesViewModelFactory = remember(repository) {
        com.example.core.viewmodel.AppViewModelFactory {
            com.example.ui.feature.movies.MoviesViewModel(repository)
        }
    }
    val moviesViewModel: com.example.ui.feature.movies.MoviesViewModel = viewModel(factory = moviesViewModelFactory)
    val moviesState by moviesViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(profile) {
        moviesViewModel.onProfileChanged(profile)
    }

    // Series ViewModel
    val seriesViewModelFactory = remember(repository) {
        com.example.core.viewmodel.AppViewModelFactory {
            com.example.ui.feature.series.SeriesViewModel(repository)
        }
    }
    val seriesViewModel: com.example.ui.feature.series.SeriesViewModel = viewModel(factory = seriesViewModelFactory)
    val seriesState by seriesViewModel.uiState.collectAsStateWithLifecycle()
    val seriesDetailsUiState by seriesViewModel.detailsUiState.collectAsStateWithLifecycle()

    LaunchedEffect(profile) {
        seriesViewModel.onProfileChanged(profile)
    }

    // Search ViewModel
    val searchViewModelFactory = remember(repository) {
        com.example.core.viewmodel.AppViewModelFactory {
            com.example.ui.feature.search.SearchViewModel(repository)
        }
    }
    val searchViewModel: com.example.ui.feature.search.SearchViewModel = viewModel(factory = searchViewModelFactory)
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(profile) {
        searchViewModel.onProfileChanged(profile)
    }

    // Settings ViewModel
    val settingsViewModelFactory = remember(repository) {
        com.example.core.viewmodel.AppViewModelFactory {
            com.example.ui.feature.settings.SettingsViewModel(repository)
        }
    }
    val settingsViewModel: com.example.ui.feature.settings.SettingsViewModel = viewModel(factory = settingsViewModelFactory)
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(profile) {
        settingsViewModel.onProfileChanged(profile)
    }

    // Handle single-shot TMDB events from HomeViewModel
    LaunchedEffect(homeViewModel) {
        homeViewModel.events.collect { event ->
            when (event) {
                is HomeEvent.PlayMovie -> {
                    android.widget.Toast.makeText(
                        context,
                        "Matched: Playing \"${event.movie.title}\"...",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    onPlayMovie(event.movie)
                }
                is HomeEvent.OpenSeries -> {
                    android.widget.Toast.makeText(
                        context,
                        "Matched: \"${event.series.title}\"",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    seriesViewModel.selectSeries(event.series)
                }
            }
        }
    }

    // Multi-view ViewModel
    val multiViewViewModelFactory = remember {
        com.example.core.viewmodel.AppViewModelFactory {
            com.example.ui.feature.multiview.MultiViewViewModel()
        }
    }
    val multiViewViewModel: com.example.ui.feature.multiview.MultiViewViewModel = viewModel(factory = multiViewViewModelFactory)
    val multiViewState by multiViewViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(profile) {
        multiViewViewModel.onProfileChanged(profile)
    }

    val activeMultiViewChannels = multiViewState.activeMultiViewChannels
    val showMultiViewSetup = multiViewState.showMultiViewSetup
    val pendingMultiViewChannels = multiViewState.pendingMultiViewChannels

    val multiViewEnabled = FeatureAvailabilityPolicy.shouldShowMultiView(profile.features)

    LaunchedEffect(profile.id, profile.providerId, profile.features) {
        navigationState.onFeaturesChanged(profile.features)
        
        if (!profile.features.seriesEnabled) {
            seriesViewModel.selectSeries(null)
        }
        if (!profile.features.moviesEnabled && !profile.features.seriesEnabled) {
            homeViewModel.dismissUnavailableDialog()
        }
    }

    LaunchedEffect(addToMultiViewChannel, multiViewEnabled) {
        addToMultiViewChannel?.let { channel ->
            if (multiViewEnabled) {
                multiViewViewModel.addToPending(channel)
            }
            onAddToMultiViewHandled()
        }
    }

    val displayMovies = remember(moviesState.movies, moviesState.categories) {
        val visibleIds = moviesState.categories.map { it.id }.toSet()
        moviesState.movies.filter { it.categoryId in visibleIds }
    }
    val displaySeries = remember(seriesState.seriesList, seriesState.categories) {
        val visibleIds = seriesState.categories.map { it.id }.toSet()
        seriesState.seriesList.filter { it.categoryId in visibleIds }
    }

    // Fullscreen Overlay Layers for Multi-view
    if (multiViewEnabled && activeMultiViewChannels != null) {
        MultiViewPlayerScreen(
            channels = activeMultiViewChannels,
            allChannels = liveState.channels,
            onBack = { multiViewViewModel.updateActiveChannels(null) },
            profile = profile
        )
    } else if (multiViewEnabled && showMultiViewSetup) {
        MultiViewSetupView(
            allChannels = liveState.channels,
            categories = liveState.categories,
            pendingMultiViewChannels = pendingMultiViewChannels,
            profile = profile,
            onLaunch = { selected ->
                multiViewViewModel.launchMultiView(selected)
            },
            onCancel = {
                multiViewViewModel.showSetup(false)
            }
        )
    } else {        // Layout Scaffold
        AppShell(
            profile = profile,
            deviceType = deviceType,
            navigationState = navigationState
        ) { activeDestination ->
            // Main Active Panel View
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Color(profile.branding.backgroundColor))
                    .padding(16.dp)
            ) {
                when (activeDestination) {
                    AppDestination.HOME -> HomeDashboardView(
                        profile = profile,
                        favorites = homeFavorites,
                        continueWatching = homeContinueWatching,
                        recentlyWatched = homeRecentlyWatched,
                        onPlayLive = onPlayLive,
                        onPlayMovie = onPlayMovie,
                        onPlayEpisode = onPlayEpisode,
                        onSeriesClick = seriesViewModel::selectSeries,
                        isTv = isTv,
                        onTabSelected = { navigationState.navigateTo(AppDestination.fromKey(it) ?: AppDestination.HOME, profile.features) },
                        onToggleFavorite = homeViewModel::toggleFavorite,
                        homeUiState = homeUiState,
                        onTmdbItemClick = homeViewModel::onTmdbItemClick,
                        footballMatches = footballState.matches,
                        isLoadingFootball = footballState.scheduleLoading,
                        footballLoadError = footballState.scheduleError,
                        selectedFootballCompetitionKeys = footballState.selectedCompetitionKeys.toList(),
                        onRetryFootball = { footballViewModel.retrySchedule(profile.providerId) },
                        onConfigureFootball = { footballViewModel.openSettingsDialog() },
                        showFootballScheduleOnHome = footballState.showOnHome,
                        onAddToMultiView = if (multiViewEnabled) {
                                    { channel ->
                                        multiViewViewModel.addToPending(channel)
                                    }
                                } else null
                    )
                    AppDestination.LIVE -> if (profile.features.liveTvEnabled) {
                        LiveChannelsView(
                            channels = liveState.channels,
                            categories = liveState.categories,
                            selectedCategory = liveState.selectedCategoryId,
                            onCategorySelected = liveViewModel::selectCategory,
                            isTv = isTv,
                            profile = profile,
                            favoritesEnabled = liveState.favoritesEnabled,
                            favoriteChannelIds = liveState.favoriteChannelIds,
                            favoriteMutationChannelIds = liveState.favoriteMutationChannelIds,
                            favoritesError = liveState.favoritesError,
                            onToggleFavorite = liveViewModel::toggleFavorite,
                            onDismissFavoritesError = liveViewModel::dismissFavoritesError,
                            onStartMultiViewSetup = if (multiViewEnabled) {
                                {
                                    multiViewViewModel.setPendingChannels(emptyList()) // start fresh
                                    multiViewViewModel.showSetup(true)
                                }
                            } else null,
                            initialLoading = liveState.initialLoading,
                            refreshing = liveState.refreshing,
                            channelsError = liveState.channelsError,
                            onRetryChannels = liveViewModel::retryChannels,
                            categoriesError = liveState.categoriesError,
                            categoriesLoading = liveState.categoriesLoading,
                            onRetryCategories = liveViewModel::retryCategories,

                            // Parental Controls Properties
                            parentalControlsEnabled = liveState.parentalControlsEnabled,
                            parentalLoading = liveState.parentalLoading,
                            parentalLoadError = liveState.parentalLoadError,
                            pinDialogVisible = liveState.pinDialogVisible,
                            pinVerificationLoading = liveState.pinVerificationLoading,
                            pinVerificationError = liveState.pinVerificationError,
                            lockedLiveCategoryIds = liveState.lockedLiveCategoryIds,
                            hideAdultContent = liveState.hideAdultContent,
                            onChannelSelected = liveViewModel::onChannelSelected,
                            onSubmitParentalPin = liveViewModel::submitParentalPin,
                            onCancelParentalDialog = liveViewModel::cancelParentalDialog,
                            onRetryParentalStatus = liveViewModel::retryParentalStatus,
                            onDismissParentalLoadError = liveViewModel::dismissParentalLoadError
                        )
                    }
                    AppDestination.MOVIES -> if (profile.features.moviesEnabled) {
                        MoviesLibraryView(
                            movies = displayMovies,
                            categories = moviesState.categories,
                            selectedCategory = moviesState.selectedCategoryId,
                            onCategorySelected = moviesViewModel::selectCategory,
                            onPlayMovie = onPlayMovie,
                            isTv = isTv,
                            profile = profile,
                            favorites = moviesState.favorites,
                            onToggleFavorite = moviesViewModel::toggleFavorite,
                            isLoading = moviesState.isLoading,
                            error = moviesState.error,
                            onRetry = moviesViewModel::loadMovies
                        )
                    }
                    AppDestination.SERIES -> if (profile.features.seriesEnabled) {
                        SeriesLibraryView(
                            seriesList = displaySeries,
                            categories = seriesState.categories,
                            selectedCategory = seriesState.selectedCategoryId,
                            onCategorySelected = seriesViewModel::selectCategory,
                            onSeriesClick = seriesViewModel::selectSeries,
                            isTv = isTv,
                            profile = profile,
                            favorites = seriesState.favorites,
                            onToggleFavorite = seriesViewModel::toggleFavorite,
                            isLoading = seriesState.isLoading,
                            error = seriesState.error,
                            onRetry = seriesViewModel::loadSeries
                        )
                    }
                    AppDestination.EPG -> if (profile.features.epgEnabled && profile.features.liveTvEnabled) {
                        TvGuideView(
                            uiState = epgState,
                            onSelectChannel = epgViewModel::selectChannel,
                            onRequestPlay = liveViewModel::onChannelSelected,
                            onRetryPrograms = epgViewModel::retryPrograms,
                            onDismissProgramsError = epgViewModel::dismissProgramsError,
                            parentalControlsEnabled = liveState.parentalControlsEnabled,
                            parentalLoading = liveState.parentalLoading,
                            parentalLoadError = liveState.parentalLoadError,
                            pinDialogVisible = liveState.pinDialogVisible,
                            pinVerificationLoading = liveState.pinVerificationLoading,
                            pinVerificationError = liveState.pinVerificationError,
                            lockedLiveCategoryIds = liveState.lockedLiveCategoryIds,
                            onSubmitParentalPin = liveViewModel::submitParentalPin,
                            onCancelParentalDialog = liveViewModel::cancelParentalDialog,
                            onRetryParentalStatus = liveViewModel::retryParentalStatus,
                            onDismissParentalLoadError = liveViewModel::dismissParentalLoadError,
                            isTv = isTv,
                            profile = profile
                        )
                    }
                    AppDestination.SEARCH -> if (profile.features.searchEnabled) {
                        SearchPanel(
                            query = searchState.query,
                            onQueryChanged = searchViewModel::onQueryChanged,
                            results = searchState.results,
                            onPlayLive = onPlayLive,
                            onPlayMovie = onPlayMovie,
                            onSeriesClick = seriesViewModel::selectSeries,
                            isTv = isTv,
                            profile = profile,
                            favorites = searchState.favorites,
                            onToggleFavorite = searchViewModel::toggleFavorite,
                            isLoading = searchState.isLoading,
                            error = searchState.error,
                            onRetry = searchViewModel::retry
                        )
                    }
                    AppDestination.SETTINGS -> SettingsView(
                        profile = profile,
                        onProfileSelected = onProfileSelected,
                        uiState = settingsState,
                        onRefreshCache = settingsViewModel::refreshCache,
                        onSetSubScreen = settingsViewModel::setSubScreen,
                        onShowProfileDialog = settingsViewModel::showProfileDialog,
                        onSelectTab = settingsViewModel::selectTab,
                        onReorderCategories = settingsViewModel::reorderCategories,
                        onSetCategoryPinned = settingsViewModel::setCategoryPinned,
                        onSetCategoryHidden = settingsViewModel::setCategoryHidden,
                        onResetCategoryCustomization = settingsViewModel::resetCategoryCustomization,
                        onNavigateToSupport = onNavigateToSupport,
                        onNavigateToParental = onNavigateToParental,
                        onNavigateToSearch = { navigationState.navigateTo(AppDestination.SEARCH, profile.features) },
                        onLogout = onLogout,
                        isTv = isTv,
                        selectedFootballCompetitionCount = footballState.selectedCompetitionKeys.size,
                        onConfigureFootball = footballViewModel::openSettingsDialog,
                        onNavigateToEpg = { navigationState.navigateTo(AppDestination.EPG, profile.features) },
                        onDismissCategoryError = settingsViewModel::clearCategoryError
                    )
                }
            }
        }
    }

    seriesDetailsUiState.activeSeries?.let { series ->
        SeriesDetailsDialog(
            series = series,
            detailsUiState = seriesDetailsUiState,
            profile = profile,
            onSelectSeason = seriesViewModel::selectSeason,
            onPlayEpisode = onPlayEpisode,
            onDismiss = { seriesViewModel.selectSeries(null) },
            onRetry = seriesViewModel::retryDetails
        )
    }

    unavailableTmdbItem?.let { item ->
        UnavailableTmdbItemDialog(
            item = item,
            profile = profile,
            onDismiss = homeViewModel::dismissUnavailableDialog
        )
    }

    if (footballState.featureEnabled && footballState.setupDialogVisible) {
        FootballConfigDialog(
            onDismiss = { footballViewModel.closeSetupDialog() },
            profile = profile,
            competitions = footballState.competitions,
            selectedKeys = footballState.draftCompetitionKeys,
            isLoading = footballState.competitionsLoading,
            errorMessage = footballState.competitionsError,
            onToggleCompetition = footballViewModel::selectCompetition,
            onSelectAll = footballViewModel::selectAllCompetitions,
            onClearAll = footballViewModel::clearCompetitionSelection,
            onRetry = {
                footballViewModel.loadCompetitions(
                    profile.providerId,
                    forceRefresh = true
                )
            },
            onSave = {
                footballViewModel.saveInitialSetupSelection(
                    footballState.draftCompetitionKeys,
                    profile.providerId
                )
            },
            hasSeenSetup = footballState.hasSeenSetup
        )
    }

    if (footballState.featureEnabled && footballState.settingsDialogVisible) {
        FootballConfigDialog(
            onDismiss = { footballViewModel.closeSettingsDialog() },
            profile = profile,
            competitions = footballState.competitions,
            selectedKeys = footballState.draftCompetitionKeys,
            isLoading = footballState.competitionsLoading,
            errorMessage = footballState.competitionsError,
            onToggleCompetition = footballViewModel::selectCompetition,
            onSelectAll = footballViewModel::selectAllCompetitions,
            onClearAll = footballViewModel::clearCompetitionSelection,
            onRetry = {
                footballViewModel.loadCompetitions(
                    profile.providerId,
                    forceRefresh = true
                )
            },
            onSave = {
                footballViewModel.saveSettingsSelection(
                    footballState.draftCompetitionKeys,
                    profile.providerId
                )
            },
            hasSeenSetup = footballState.hasSeenSetup
        )
    }
}
