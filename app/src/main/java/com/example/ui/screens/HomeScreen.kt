package com.example.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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
import com.example.config.ProviderConfigRegistry
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HomeScreen(
    repository: IptvRepository,
    addToMultiViewChannel: LiveChannel? = null,
    onAddToMultiViewHandled: () -> Unit = {},
    onPlayLive: (LiveChannel) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onPlayEpisode: (Series, Episode) -> Unit,
    onNavigateToSupport: () -> Unit,
    onNavigateToParental: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    // Dynamic state triggered by active provider profile configurations
    val profile = ProviderConfigRegistry.currentProfile
    var activeTab by remember { mutableStateOf("HOME") }
    val coroutineScope = rememberCoroutineScope()

    // Home recommendations ViewModel state
    val homeViewModel = remember(repository) { HomeViewModel(repository) }
    val homeUiState by homeViewModel.uiState.collectAsState()

    LaunchedEffect(profile) {
        homeViewModel.loadHomeData(profile.providerId)
    }

    // Repository states
    val favorites by repository.favorites.collectAsState(initial = emptyList())

    val toggleFavorite: (FavoriteEntity) -> Unit = { favorite ->
        coroutineScope.launch {
            val isFav = favorites.any { it.contentId == favorite.contentId && it.contentType == favorite.contentType }
            if (isFav) {
                repository.removeFavorite(favorite.contentId, favorite.contentType)
            } else {
                repository.addFavorite(favorite)
            }
        }
    }
    val continueWatching by repository.continueWatching.collectAsState(initial = emptyList())
    val recentlyWatched by repository.recentlyWatched.collectAsState(initial = emptyList())

    // Football states
    var footballMatches by remember { mutableStateOf<List<FootballWatchMatch>>(emptyList()) }
    var isLoadingFootball by remember { mutableStateOf(false) }
    var footballLoadError by remember { mutableStateOf<String?>(null) }
    var footballRetryCount by remember { mutableStateOf(0) }
    var showFootballSetupDialog by remember { mutableStateOf(false) }
    var showFootballSettingsDialog by remember { mutableStateOf(false) }

    var selectedFootballCompetitionKeys by remember {
        mutableStateOf(repository.footballPrefs.selectedFootballCompetitionKeys)
    }

    // Multi-view states
    var activeMultiViewChannels by remember { mutableStateOf<List<LiveChannel>?>(null) }
    var showMultiViewSetup by remember { mutableStateOf(false) }
    var pendingMultiViewChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }

    LaunchedEffect(addToMultiViewChannel) {
        addToMultiViewChannel?.let { channel ->
            if (pendingMultiViewChannels.none { it.id == channel.id }) {
                pendingMultiViewChannels = pendingMultiViewChannels + channel
            }
            showMultiViewSetup = true
            onAddToMultiViewHandled()
        }
    }

    LaunchedEffect(activeTab, profile.features.footballScheduleEnabled) {
        if (activeTab == "HOME" && profile.features.footballScheduleEnabled && !repository.footballPrefs.hasSeenFootballCompetitionSetup) {
            showFootballSetupDialog = true
        }
    }

    LaunchedEffect(
        activeTab,
        selectedFootballCompetitionKeys,
        repository.footballPrefs.showFootballScheduleOnHome,
        profile.features.footballScheduleEnabled,
        footballRetryCount
    ) {
        if (
            activeTab == "HOME" &&
            profile.features.footballScheduleEnabled &&
            repository.footballPrefs.showFootballScheduleOnHome &&
            selectedFootballCompetitionKeys.isNotEmpty()
        ) {
            val loadId =
                SystemClock.elapsedRealtime()

            android.util.Log.d(
                "FootballTrace",
                "UI_START id=$loadId " +
                    "competitions=${selectedFootballCompetitionKeys.size} " +
                    "retry=$footballRetryCount"
            )

            isLoadingFootball = true
            footballLoadError = null

            try {
                footballMatches =
                    repository.getFootballSchedule(
                        providerId =
                            profile.providerId,

                        selectedCompetitionKeys =
                            selectedFootballCompetitionKeys
                    )

                android.util.Log.d(
                    "FootballTrace",
                    "UI_SUCCESS id=$loadId " +
                        "matches=${footballMatches.size}"
                )
            } catch (
                e: TimeoutCancellationException
            ) {
                android.util.Log.e(
                    "FootballTrace",
                    "UI_TIMEOUT id=$loadId",
                    e
                )

                footballMatches =
                    emptyList()

                footballLoadError =
                    "Football schedule request timed out."
            } catch (
                e: CancellationException
            ) {
                android.util.Log.d(
                    "FootballTrace",
                    "UI_CANCELLED id=$loadId"
                )

                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "FootballTrace",
                    "UI_ERROR id=$loadId",
                    e
                )

                footballMatches =
                    emptyList()

                footballLoadError =
                    "Could not load football schedule."
            } finally {
                isLoadingFootball = false

                android.util.Log.d(
                    "FootballTrace",
                    "UI_FINALLY id=$loadId " +
                        "loading=$isLoadingFootball"
                )
            }
        } else {
            footballMatches = emptyList()
            footballLoadError = null
            isLoadingFootball = false
        }
    }

    // UI Local lists
    var liveChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var moviesList by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var categoriesLive by remember { mutableStateOf<List<Category>>(emptyList()) }
    var categoriesMovie by remember { mutableStateOf<List<Category>>(emptyList()) }
    var categoriesSeries by remember { mutableStateOf<List<Category>>(emptyList()) }

    var selectedCategoryLive by remember { mutableStateOf<String?>(null) }
    var selectedCategoryMovie by remember { mutableStateOf<String?>(null) }
    var selectedCategorySeries by remember { mutableStateOf<String?>(null) }

    // Loading & search flows
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf(SearchResults()) }
    var activeSeriesDetail by remember { mutableStateOf<Series?>(null) }
    var unavailableTmdbItem by remember { mutableStateOf<HomeItem?>(null) }

    // Initialize content flows
    LaunchedEffect(profile, activeTab, selectedCategoryLive, selectedCategoryMovie, selectedCategorySeries) {
        if (profile.features.liveTvEnabled) {
            repository.getCategories("LIVE").first() // Seed cache
            liveChannels = repository.getLiveChannels(selectedCategoryLive).first()
        }
        if (profile.features.moviesEnabled) {
            repository.getCategories("MOVIE").first() // Seed cache
            moviesList = repository.getMovies(selectedCategoryMovie).first()
        }
        if (profile.features.seriesEnabled) {
            repository.getCategories("SERIES").first() // Seed cache
            seriesList = repository.getSeries(selectedCategorySeries).first()
        }
    }

    LaunchedEffect(repository) {
        repository.observeVisibleCategories("LIVE").collect { categoriesLive = it }
    }
    LaunchedEffect(repository) {
        repository.observeVisibleCategories("MOVIE").collect { categoriesMovie = it }
    }
    LaunchedEffect(repository) {
        repository.observeVisibleCategories("SERIES").collect { categoriesSeries = it }
    }

    val displayChannels = remember(liveChannels, categoriesLive) {
        val visibleIds = categoriesLive.map { it.id }.toSet()
        liveChannels.filter { it.categoryId in visibleIds }
    }
    val displayMovies = remember(moviesList, categoriesMovie) {
        val visibleIds = categoriesMovie.map { it.id }.toSet()
        moviesList.filter { it.categoryId in visibleIds }
    }
    val displaySeries = remember(seriesList, categoriesSeries) {
        val visibleIds = categoriesSeries.map { it.id }.toSet()
        seriesList.filter { it.categoryId in visibleIds }
    }

    // Live search executor
    LaunchedEffect(searchQuery, activeTab, categoriesLive, categoriesMovie, categoriesSeries) {
        if (searchQuery.isNotEmpty()) {
            repository.searchContent(
                searchQuery,
                profile.features.liveTvEnabled,
                profile.features.moviesEnabled,
                profile.features.seriesEnabled
            ).collect { results ->
                val visibleLiveIds = categoriesLive.map { it.id }.toSet()
                val visibleMovieIds = categoriesMovie.map { it.id }.toSet()
                val visibleSeriesIds = categoriesSeries.map { it.id }.toSet()
                searchResult = results.copy(
                    liveChannels = results.liveChannels.filter { it.categoryId in visibleLiveIds },
                    movies = results.movies.filter { it.categoryId in visibleMovieIds },
                    series = results.series.filter { it.categoryId in visibleSeriesIds }
                )
            }
        } else {
            searchResult = SearchResults()
        }
    }

    // Fullscreen Overlay Layers for Multi-view
    if (activeMultiViewChannels != null) {
        MultiViewPlayerScreen(
            channels = activeMultiViewChannels!!,
            allChannels = liveChannels,
            onBack = { activeMultiViewChannels = null },
            profile = profile
        )
    } else if (showMultiViewSetup) {
        MultiViewSetupView(
            allChannels = liveChannels,
            categories = categoriesLive,
            pendingMultiViewChannels = pendingMultiViewChannels,
            profile = profile,
            onLaunch = { selected ->
                activeMultiViewChannels = selected
                showMultiViewSetup = false
            },
            onCancel = {
                showMultiViewSetup = false
            }
        )
    } else {
        // Layout Scaffold
        Scaffold(
            bottomBar = {
                if (!isTv) {
                    PhoneBottomNavBar(
                        activeTab = activeTab,
                        onTabSelected = { activeTab = it },
                        profile = profile
                    )
                }
            },
            containerColor = Color(profile.branding.backgroundColor)
        ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (!isTv) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            // TV Left Navigation Rail
            if (isTv) {
                TvNavigationRail(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    profile = profile
                )
            }

            // Main Active Panel View
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(Color(profile.branding.backgroundColor))
                    .padding(16.dp)
            ) {
                when (activeTab) {
                    "HOME" -> HomeDashboardView(
                        profile = profile,
                        repository = repository,
                        favorites = favorites,
                        continueWatching = continueWatching,
                        recentlyWatched = recentlyWatched,
                        onPlayLive = onPlayLive,
                        onPlayMovie = onPlayMovie,
                        onPlayEpisode = onPlayEpisode,
                        onSeriesClick = { activeSeriesDetail = it },
                        isTv = isTv,
                        onTabSelected = { activeTab = it },
                        onToggleFavorite = toggleFavorite,
                        homeUiState = homeUiState,
                        footballMatches = footballMatches,
                        isLoadingFootball = isLoadingFootball,
                        footballLoadError = footballLoadError,
                        selectedFootballCompetitionKeys = selectedFootballCompetitionKeys,
                        onRetryFootball = { footballRetryCount++ },
                        onConfigureFootball = { showFootballSettingsDialog = true },
                        onAddToMultiView = { channel ->
                            if (pendingMultiViewChannels.none { it.id == channel.id }) {
                                pendingMultiViewChannels = pendingMultiViewChannels + channel
                            }
                            showMultiViewSetup = true
                        },
                        onTmdbItemClick = { item ->
                            coroutineScope.launch {
                                val itemTitle = item.title ?: "Untitled"
                                android.widget.Toast.makeText(
                                    context,
                                    "Searching IPTV provider for \"$itemTitle\"...",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                
                                if (item.mediaType == "tv") {
                                    val matched = repository.findMatchingSeries(itemTitle)
                                    val decision = TmdbClickDecisionProcessor.processClick(item, null, matched)
                                    when (decision) {
                                        is TmdbClickDecisionProcessor.TmdbClickResult.OpenSeries -> {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Matched: \"${decision.series.title}\"",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            activeSeriesDetail = decision.series
                                        }
                                        is TmdbClickDecisionProcessor.TmdbClickResult.Unavailable -> {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Not available in your provider library.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            unavailableTmdbItem = decision.item
                                        }
                                        else -> {}
                                    }
                                } else {
                                    val matched = repository.findMatchingMovie(itemTitle)
                                    val decision = TmdbClickDecisionProcessor.processClick(item, matched, null)
                                    when (decision) {
                                        is TmdbClickDecisionProcessor.TmdbClickResult.PlayMovie -> {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Matched: Playing \"${decision.movie.title}\"...",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            onPlayMovie(decision.movie)
                                        }
                                        is TmdbClickDecisionProcessor.TmdbClickResult.Unavailable -> {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Not available in your provider library.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            unavailableTmdbItem = decision.item
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    )
                    "LIVE" -> if (profile.features.liveTvEnabled) {
                        LiveChannelsView(
                            channels = displayChannels,
                            categories = categoriesLive,
                            selectedCategory = selectedCategoryLive,
                            onCategorySelected = { selectedCategoryLive = it },
                            onPlayLive = onPlayLive,
                            repository = repository,
                            isTv = isTv,
                            profile = profile,
                            favorites = favorites,
                            onToggleFavorite = toggleFavorite,
                            onStartMultiViewSetup = {
                                pendingMultiViewChannels = emptyList() // start fresh
                                showMultiViewSetup = true
                            }
                        )
                    }
                    "MOVIES" -> if (profile.features.moviesEnabled) {
                        MoviesLibraryView(
                            movies = displayMovies,
                            categories = categoriesMovie,
                            selectedCategory = selectedCategoryMovie,
                            onCategorySelected = { selectedCategoryMovie = it },
                            onPlayMovie = onPlayMovie,
                            repository = repository,
                            isTv = isTv,
                            profile = profile,
                            favorites = favorites,
                            onToggleFavorite = toggleFavorite
                        )
                    }
                    "SERIES" -> if (profile.features.seriesEnabled) {
                        SeriesLibraryView(
                            seriesList = displaySeries,
                            categories = categoriesSeries,
                            selectedCategory = selectedCategorySeries,
                            onCategorySelected = { selectedCategorySeries = it },
                            onSeriesClick = { activeSeriesDetail = it },
                            repository = repository,
                            isTv = isTv,
                            profile = profile,
                            favorites = favorites,
                            onToggleFavorite = toggleFavorite
                        )
                    }
                    "EPG" -> if (profile.features.epgEnabled && profile.features.liveTvEnabled) {
                        TvGuideView(
                            channels = displayChannels,
                            repository = repository,
                            onPlayLive = onPlayLive,
                            isTv = isTv,
                            profile = profile
                        )
                    }
                    "SEARCH" -> if (profile.features.searchEnabled) {
                        SearchPanel(
                            query = searchQuery,
                            onQueryChanged = { searchQuery = it },
                            results = searchResult,
                            onPlayLive = onPlayLive,
                            onPlayMovie = onPlayMovie,
                            onSeriesClick = { activeSeriesDetail = it },
                            isTv = isTv,
                            profile = profile,
                            favorites = favorites,
                            onToggleFavorite = toggleFavorite
                        )
                    }
                    "SETTINGS" -> SettingsView(
                        profile = profile,
                        repository = repository,
                        onNavigateToSupport = onNavigateToSupport,
                        onNavigateToParental = onNavigateToParental,
                        onLogout = onLogout,
                        isTv = isTv,
                        selectedFootballCompetitionCount = selectedFootballCompetitionKeys.size,
                        onConfigureFootball = { showFootballSettingsDialog = true }
                    )
                }
            }
        }
    }
}

    activeSeriesDetail?.let { series ->
        SeriesDetailsDialog(
            series = series,
            repository = repository,
            profile = profile,
            onPlayEpisode = onPlayEpisode,
            onDismiss = { activeSeriesDetail = null }
        )
    }

    unavailableTmdbItem?.let { item ->
        UnavailableTmdbItemDialog(
            item = item,
            profile = profile,
            onDismiss = { unavailableTmdbItem = null }
        )
    }

    if (showFootballSetupDialog) {
        FootballConfigDialog(
            onDismiss = { showFootballSetupDialog = false },
            profile = profile,
            repository = repository,
            onSelectionSaved = { newKeys ->
                selectedFootballCompetitionKeys = newKeys
            }
        )
    }

    if (showFootballSettingsDialog) {
        FootballConfigDialog(
            onDismiss = { showFootballSettingsDialog = false },
            profile = profile,
            repository = repository,
            onSelectionSaved = { newKeys ->
                selectedFootballCompetitionKeys = newKeys
            }
        )
    }
}

// --- Dynamic Navigation Components ---

@Composable
fun PhoneBottomNavBar(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    profile: com.example.config.ProviderProfile
) {
    NavigationBar(
        containerColor = Color(profile.branding.surfaceColor),
        contentColor = Color.White
    ) {
        NavigationBarItem(
            selected = activeTab == "HOME",
            onClick = { onTabSelected("HOME") },
            icon = { Icon(Icons.Default.Home, "Home") },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = Color(profile.branding.primaryColor),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_home")
        )
        if (profile.features.liveTvEnabled) {
            NavigationBarItem(
                selected = activeTab == "LIVE",
                onClick = { onTabSelected("LIVE") },
                icon = { Icon(Icons.Default.Tv, "Live") },
                label = { Text("Live") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(profile.branding.primaryColor),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_live")
            )
        }
        if (profile.features.moviesEnabled) {
            NavigationBarItem(
                selected = activeTab == "MOVIES",
                onClick = { onTabSelected("MOVIES") },
                icon = { Icon(Icons.Default.Movie, "Movies") },
                label = { Text("Movies") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(profile.branding.primaryColor),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_movies")
            )
        }
        if (profile.features.seriesEnabled) {
            NavigationBarItem(
                selected = activeTab == "SERIES",
                onClick = { onTabSelected("SERIES") },
                icon = { Icon(Icons.Default.VideoLibrary, "Series") },
                label = { Text("Series") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(profile.branding.primaryColor),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_series")
            )
        }
        NavigationBarItem(
            selected = activeTab == "SETTINGS",
            onClick = { onTabSelected("SETTINGS") },
            icon = { Icon(Icons.Default.Settings, "Settings") },
            label = { Text("More") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = Color(profile.branding.primaryColor),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_settings")
        )
    }
}

@Composable
fun TvNavigationRail(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    profile: com.example.config.ProviderProfile
) {
    NavigationRail(
        containerColor = Color(profile.branding.surfaceColor),
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp)) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "App Logo",
                    tint = Color(profile.branding.primaryColor),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = profile.branding.logoText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TvRailItem(
                selected = activeTab == "HOME",
                onClick = { onTabSelected("HOME") },
                icon = Icons.Default.Home,
                label = "Home",
                profile = profile
            )
            if (profile.features.liveTvEnabled) {
                TvRailItem(
                    selected = activeTab == "LIVE",
                    onClick = { onTabSelected("LIVE") },
                    icon = Icons.Default.Tv,
                    label = "Live TV",
                    profile = profile
                )
            }
            if (profile.features.epgEnabled && profile.features.liveTvEnabled) {
                TvRailItem(
                    selected = activeTab == "EPG",
                    onClick = { onTabSelected("EPG") },
                    icon = Icons.Default.CalendarMonth,
                    label = "Guide",
                    profile = profile
                )
            }
            if (profile.features.moviesEnabled) {
                TvRailItem(
                    selected = activeTab == "MOVIES",
                    onClick = { onTabSelected("MOVIES") },
                    icon = Icons.Default.Movie,
                    label = "Movies",
                    profile = profile
                )
            }
            if (profile.features.seriesEnabled) {
                TvRailItem(
                    selected = activeTab == "SERIES",
                    onClick = { onTabSelected("SERIES") },
                    icon = Icons.Default.VideoLibrary,
                    label = "Series",
                    profile = profile
                )
            }
            TvRailItem(
                selected = activeTab == "SETTINGS",
                onClick = { onTabSelected("SETTINGS") },
                icon = Icons.Default.Settings,
                label = "Settings",
                profile = profile
            )
        }
    }
}

@Composable
fun TvRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    profile: com.example.config.ProviderProfile
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> Color(profile.branding.primaryColor)
                    isFocused -> Color.White.copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected || isFocused) Color.White else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected || isFocused) Color.White else Color.Gray,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
