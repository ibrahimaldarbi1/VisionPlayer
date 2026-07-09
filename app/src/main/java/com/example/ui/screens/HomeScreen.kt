package com.example.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    var showFootballSetupDialog by remember { mutableStateOf(false) }
    var showFootballSettingsDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(activeTab) {
        if (activeTab == "HOME" && !repository.footballPrefs.hasSeenFootballSetup) {
            showFootballSetupDialog = true
        }
    }

    LaunchedEffect(activeTab, repository.footballPrefs.showFootballScheduleOnHome, repository.footballPrefs.selectedFootballCompetitionCodes, repository.footballPrefs.selectedFootballTeamIds) {
        if (activeTab == "HOME" && repository.footballPrefs.showFootballScheduleOnHome) {
            val codes = repository.footballPrefs.selectedFootballCompetitionCodes
            val ids = repository.footballPrefs.selectedFootballTeamIds
            if (codes.isNotEmpty() || ids.isNotEmpty()) {
                isLoadingFootball = true
                footballMatches = repository.getFootballSchedule(profile.providerId, codes, ids)
                isLoadingFootball = false
            } else {
                footballMatches = emptyList()
            }
        } else {
            footballMatches = emptyList()
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
            isFirstLaunch = true
        )
    }

    if (showFootballSettingsDialog) {
        FootballConfigDialog(
            onDismiss = { showFootballSettingsDialog = false },
            profile = profile,
            repository = repository,
            isFirstLaunch = false
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

// --- Home Dashboard Module ---

@Composable
fun HomeDashboardView(
    profile: com.example.config.ProviderProfile,
    repository: IptvRepository,
    favorites: List<FavoriteEntity>,
    continueWatching: List<ContinueWatchingEntity>,
    recentlyWatched: List<RecentlyWatchedEntity>,
    onPlayLive: (LiveChannel) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onPlayEpisode: (Series, Episode) -> Unit,
    onSeriesClick: (Series) -> Unit,
    isTv: Boolean,
    onTabSelected: (String) -> Unit,
    onToggleFavorite: (FavoriteEntity) -> Unit = {},
    homeUiState: HomeUiState = HomeUiState.Loading,
    onTmdbItemClick: (HomeItem) -> Unit = {},
    footballMatches: List<FootballWatchMatch> = emptyList(),
    isLoadingFootball: Boolean = false,
    onAddToMultiView: ((LiveChannel) -> Unit)? = null
) {
    val hasLocalContent = recentlyWatched.isNotEmpty() || continueWatching.isNotEmpty() || favorites.isNotEmpty()
    val hasDynamicContent = homeUiState is HomeUiState.Success && (homeUiState.homeResponse.rows?.any { it.items?.isNotEmpty() == true } == true)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (!hasLocalContent && !hasDynamicContent && homeUiState !is HomeUiState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty Dashboard",
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Welcome to Vision Player!",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Use the navigation bar above to browse Live TV, Movies, or TV Shows.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // --- DYNAMIC HOMESCREEN ROWS (Trending recommendation rows from the backend API) ---
        if (homeUiState is HomeUiState.Success) {
            val rows = homeUiState.homeResponse.rows ?: emptyList()
            rows.forEach { row ->
                val items = row.items ?: emptyList()
                if (items.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = row.title ?: "Recommendations",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(items) { item ->
                                    FocusableItemCard(
                                        title = item.title ?: "Untitled",
                                        imageUrl = item.posterUrl ?: "",
                                        subtitle = if (item.mediaType == "tv") "TV Series" else "Movie",
                                        onClick = { onTmdbItemClick(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (homeUiState is HomeUiState.Loading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(profile.branding.primaryColor),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Loading recommendations...",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // --- FOOTBALL SCHEDULE ROW (Only if showFootballScheduleOnHome is true and selected are not empty) ---
        val showFootballRow = repository.footballPrefs.showFootballScheduleOnHome
        val hasSelection = repository.footballPrefs.selectedFootballCompetitionCodes.isNotEmpty() ||
                repository.footballPrefs.selectedFootballTeamIds.isNotEmpty()

        if (showFootballRow && hasSelection) {
            item {
                Column {
                    Text(
                        text = "Football Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (isLoadingFootball) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(profile.branding.primaryColor))
                        }
                    } else if (footballMatches.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(profile.branding.surfaceColor), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No football matches found for your current selection.",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        val context = LocalContext.current
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("football_schedule_row")
                        ) {
                            items(footballMatches) { watchMatch ->
                                FootballMatchCard(
                                    watchMatch = watchMatch,
                                    primaryColor = Color(profile.branding.primaryColor),
                                    surfaceColor = Color(profile.branding.surfaceColor),
                                    onWatchClick = { channel ->
                                        try {
                                            onPlayLive(channel)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Cannot open matched IPTV channel: ${e.message}",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onAddToMultiView = onAddToMultiView
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Recently Watched Row (Only if live TV enabled and recently watched enabled)
        if (profile.features.liveTvEnabled && profile.features.recentlyWatchedEnabled && recentlyWatched.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Recently Watched Channels",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentlyWatched) { item ->
                            val channel = LiveChannel(
                                id = item.contentId,
                                name = item.title,
                                streamUrl = item.streamUrl,
                                logoUrl = item.posterOrLogo,
                                categoryId = "",
                                categoryName = item.categoryName,
                                epgId = "",
                                channelNumber = 0
                            )
                            FocusableItemCard(
                                title = item.title,
                                imageUrl = item.posterOrLogo,
                                subtitle = "Live Stream",
                                onClick = { onPlayLive(channel) }
                            )
                        }
                    }
                }
            }
        }

        // Continue Watching VOD progress row (Only if continue watching enabled and not empty)
        if (profile.features.continueWatchingEnabled && continueWatching.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Continue Watching",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueWatching) { progress ->
                            FocusableProgressCard(
                                title = progress.title,
                                subtitle = if (progress.contentType == "EPISODE") "S${progress.seasonNumber} E${progress.episodeNumber}" else "Movie",
                                imageUrl = progress.posterOrLogo,
                                progress = progress.positionMs.toFloat() / progress.durationMs.toFloat(),
                                onClick = {
                                    if (progress.contentType == "MOVIE") {
                                        val movie = Movie(
                                            id = progress.contentId,
                                            title = progress.title,
                                            streamUrl = progress.streamUrl,
                                            posterUrl = progress.posterOrLogo,
                                            backdropUrl = progress.posterOrLogo,
                                            categoryId = "",
                                            categoryName = "",
                                            description = ""
                                        )
                                        onPlayMovie(movie)
                                    } else {
                                        val series = Series(
                                            id = progress.parentId,
                                            title = progress.parentTitle,
                                            posterUrl = progress.posterOrLogo,
                                            backdropUrl = progress.posterOrLogo,
                                            categoryId = "",
                                            categoryName = "",
                                            description = ""
                                        )
                                        val episode = Episode(
                                            id = progress.contentId,
                                            seriesId = progress.parentId,
                                            seasonId = "",
                                            seasonNumber = progress.seasonNumber,
                                            episodeNumber = progress.episodeNumber,
                                            title = progress.title,
                                            streamUrl = progress.streamUrl,
                                            description = ""
                                        )
                                        onPlayEpisode(series, episode)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Branded Favorites Row (Only if favorites enabled and not empty)
        if (profile.features.favoritesEnabled && favorites.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "My Saved Favorites",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favorites) { fav ->
                            FocusableItemCard(
                                title = fav.title,
                                imageUrl = fav.posterOrLogo,
                                subtitle = fav.contentType,
                                isFavorite = true,
                                onFavoriteToggle = { onToggleFavorite(fav) },
                                onClick = {
                                    when (fav.contentType) {
                                        "LIVE" -> {
                                            val channel = LiveChannel(
                                                id = fav.contentId,
                                                name = fav.title,
                                                streamUrl = fav.streamUrl,
                                                logoUrl = fav.posterOrLogo,
                                                categoryId = fav.categoryId,
                                                categoryName = fav.categoryName,
                                                epgId = "",
                                                channelNumber = 0
                                            )
                                            onPlayLive(channel)
                                        }
                                        "MOVIE" -> {
                                            val movie = Movie(
                                                id = fav.contentId,
                                                title = fav.title,
                                                streamUrl = fav.streamUrl,
                                                posterUrl = fav.posterOrLogo,
                                                backdropUrl = fav.posterOrLogo,
                                                categoryId = fav.categoryId,
                                                categoryName = fav.categoryName,
                                                description = ""
                                            )
                                            onPlayMovie(movie)
                                        }
                                        "SERIES" -> {
                                            val series = Series(
                                                id = fav.contentId,
                                                title = fav.title,
                                                posterUrl = fav.posterOrLogo,
                                                backdropUrl = fav.posterOrLogo,
                                                categoryId = fav.categoryId,
                                                categoryName = fav.categoryName,
                                                description = ""
                                            )
                                            onSeriesClick(series)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

    }
}

// --- Live Channels view ---

@Composable
fun LiveChannelsView(
    channels: List<LiveChannel>,
    categories: List<Category>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onPlayLive: (LiveChannel) -> Unit,
    repository: IptvRepository,
    isTv: Boolean,
    profile: com.example.config.ProviderProfile,
    favorites: List<FavoriteEntity> = emptyList(),
    onToggleFavorite: (FavoriteEntity) -> Unit = {},
    onStartMultiViewSetup: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var isAdultUnlocked by remember { mutableStateOf(false) }
    var pinRequiredChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Check if global parental PIN is already unlocked
        val settings = repository.getParentalSettingsDirect()
        if (settings == null) {
            isAdultUnlocked = true // No PIN configured -> default allowed
        }
    }

    if (pinRequiredChannel != null) {
        AlertDialog(
            onDismissRequest = { pinRequiredChannel = null },
            title = { Text("Parental Control PIN Required") },
            text = {
                Column {
                    Text("This channel is locked or labeled as adult content.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Enter 4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(profile.branding.primaryColor),
                            focusedLabelColor = Color(profile.branding.primaryColor)
                        )
                    )
                    if (pinError) {
                        Text("Incorrect PIN. Please try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val settings = repository.getParentalSettingsDirect()
                            if (settings != null && settings.pin == pinInput) {
                                isAdultUnlocked = true
                                val chan = pinRequiredChannel
                                pinRequiredChannel = null
                                pinError = false
                                if (chan != null) onPlayLive(chan)
                            } else {
                                pinError = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                ) {
                    Text("Unlock Channel")
                }
            },
            dismissButton = {
                TextButton(onClick = { pinRequiredChannel = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Filter Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter channels by name...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Filter") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(profile.branding.primaryColor),
                focusedLabelColor = Color(profile.branding.primaryColor),
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("filter_live_channels")
        )

        if (profile.features.multiViewEnabled && onStartMultiViewSetup != null) {
            Button(
                onClick = onStartMultiViewSetup,
                colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("start_multiview_setup_button")
            ) {
                Icon(Icons.Default.GridView, contentDescription = "Multi-view")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Launch Live Multi-view Grid (Up to 4 Streams)")
            }
        }

        // Categories horizontal list
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("All Channels") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(profile.branding.primaryColor),
                        selectedLabelColor = Color.White
                    )
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat.id,
                    onClick = { onCategorySelected(cat.id) },
                    label = { Text(cat.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(profile.branding.primaryColor),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        val filteredChannels = remember(channels, searchQuery) {
            if (searchQuery.isBlank()) {
                channels
            } else {
                channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }

        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No channels match \"$searchQuery\"",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            // Live Channels vertical grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 160.dp else 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredChannels) { channel ->
                    val isFav = favorites.any { it.contentId == channel.id && it.contentType == "LIVE" }
                    FocusableItemCard(
                        title = channel.name,
                        imageUrl = channel.logoUrl,
                        subtitle = channel.categoryName,
                        isLocked = channel.isAdult,
                        isFavorite = isFav,
                        onFavoriteToggle = if (profile.features.favoritesEnabled) {
                            {
                                onToggleFavorite(
                                    FavoriteEntity(
                                        contentId = channel.id,
                                        contentType = "LIVE",
                                        title = channel.name,
                                        posterOrLogo = channel.logoUrl,
                                        streamUrl = channel.streamUrl,
                                        categoryId = channel.categoryId,
                                        categoryName = channel.categoryName
                                    )
                                )
                            }
                        } else null,
                        onClick = {
                            if (channel.isAdult && !isAdultUnlocked) {
                                pinRequiredChannel = channel
                            } else {
                                onPlayLive(channel)
                            }
                        }
                    )
                }
            }
        }
    }
}

// --- Movies Library view ---

@Composable
fun MoviesLibraryView(
    movies: List<Movie>,
    categories: List<Category>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    repository: IptvRepository,
    isTv: Boolean,
    profile: com.example.config.ProviderProfile,
    favorites: List<FavoriteEntity> = emptyList(),
    onToggleFavorite: (FavoriteEntity) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Filter Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter movies by title...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Filter") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(profile.branding.primaryColor),
                focusedLabelColor = Color(profile.branding.primaryColor),
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("filter_movies")
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("All Movies") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(profile.branding.primaryColor),
                        selectedLabelColor = Color.White
                    )
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat.id,
                    onClick = { onCategorySelected(cat.id) },
                    label = { Text(cat.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(profile.branding.primaryColor),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        val filteredMovies = remember(movies, searchQuery) {
            if (searchQuery.isBlank()) {
                movies
            } else {
                movies.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }
        }

        if (filteredMovies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No movies match \"$searchQuery\"",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 160.dp else 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredMovies) { movie ->
                    val isFav = favorites.any { it.contentId == movie.id && it.contentType == "MOVIE" }
                    FocusableItemCard(
                        title = movie.title,
                        imageUrl = movie.posterUrl,
                        subtitle = movie.genre,
                        isFavorite = isFav,
                        onFavoriteToggle = if (profile.features.favoritesEnabled) {
                            {
                                onToggleFavorite(
                                    FavoriteEntity(
                                        contentId = movie.id,
                                        contentType = "MOVIE",
                                        title = movie.title,
                                        posterOrLogo = movie.posterUrl,
                                        streamUrl = movie.streamUrl,
                                        categoryId = movie.categoryId,
                                        categoryName = movie.categoryName
                                    )
                                )
                            }
                        } else null,
                        onClick = { onPlayMovie(movie) }
                    )
                }
            }
        }
    }
}

// --- Series Library view ---

@Composable
fun SeriesLibraryView(
    seriesList: List<Series>,
    categories: List<Category>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onSeriesClick: (Series) -> Unit,
    repository: IptvRepository,
    isTv: Boolean,
    profile: com.example.config.ProviderProfile,
    favorites: List<FavoriteEntity> = emptyList(),
    onToggleFavorite: (FavoriteEntity) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Filter Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter series by title...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Filter") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(profile.branding.primaryColor),
                focusedLabelColor = Color(profile.branding.primaryColor),
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("filter_series")
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("All Series") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(profile.branding.primaryColor),
                        selectedLabelColor = Color.White
                    )
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat.id,
                    onClick = { onCategorySelected(cat.id) },
                    label = { Text(cat.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(profile.branding.primaryColor),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        val filteredSeries = remember(seriesList, searchQuery) {
            if (searchQuery.isBlank()) {
                seriesList
            } else {
                seriesList.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }
        }

        if (filteredSeries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No series match \"$searchQuery\"",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 160.dp else 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredSeries) { series ->
                    val isFav = favorites.any { it.contentId == series.id && it.contentType == "SERIES" }
                    FocusableItemCard(
                        title = series.title,
                        imageUrl = series.posterUrl,
                        subtitle = series.genre,
                        isFavorite = isFav,
                        onFavoriteToggle = if (profile.features.favoritesEnabled) {
                            {
                                onToggleFavorite(
                                    FavoriteEntity(
                                        contentId = series.id,
                                        contentType = "SERIES",
                                        title = series.title,
                                        posterOrLogo = series.posterUrl,
                                        streamUrl = "",
                                        categoryId = series.categoryId,
                                        categoryName = series.categoryName
                                    )
                                )
                            }
                        } else null,
                        onClick = { onSeriesClick(series) }
                    )
                }
            }
        }
    }
}

// --- TV Guide (EPG) Module view ---

@Composable
fun TvGuideView(
    channels: List<LiveChannel>,
    repository: IptvRepository,
    onPlayLive: (LiveChannel) -> Unit,
    isTv: Boolean,
    profile: com.example.config.ProviderProfile
) {
    var selectedChannel by remember { mutableStateOf<LiveChannel?>(channels.firstOrNull()) }
    var programList by remember { mutableStateOf<List<EpgProgramEntity>>(emptyList()) }

    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { channel ->
            repository.getEpgForChannel(channel.id).collect { list ->
                programList = list
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Vertical Channel Picker
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.4f)
                .background(Color.White.copy(alpha = 0.03f))
                .padding(8.dp)
        ) {
            Text(
                text = "EPG CHANNELS",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels) { channel ->
                    var isFocused by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .clickable { selectedChannel = channel }
                            .border(
                                width = 2.dp,
                                color = if (selectedChannel?.id == channel.id) Color(profile.branding.primaryColor) else if (isFocused) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedChannel?.id == channel.id) Color(profile.branding.primaryColor).copy(alpha = 0.2f) else Color(profile.branding.surfaceColor)
                        )
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = channel.name,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = channel.name,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Program detail blocks
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.6f)
        ) {
            selectedChannel?.let { channel ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = channel.categoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { onPlayLive(channel) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                    ) {
                        Icon(Icons.Default.PlayArrow, "Play")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Watch Channel")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (programList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No EPG information found for this channel.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(programList) { prog ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = prog.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${formatTimestamp(prog.startTime)} - ${formatTimestamp(prog.endTime)}",
                                    color = Color(profile.branding.primaryColor),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = prog.description,
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Search Panel ---

@Composable
fun SearchPanel(
    query: String,
    onQueryChanged: (String) -> Unit,
    results: SearchResults,
    onPlayLive: (LiveChannel) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    isTv: Boolean,
    profile: com.example.config.ProviderProfile,
    favorites: List<FavoriteEntity> = emptyList(),
    onToggleFavorite: (FavoriteEntity) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Search TV, Movies, and Series...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(profile.branding.primaryColor),
                focusedLabelColor = Color(profile.branding.primaryColor)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("search_text_input")
        )

        if (query.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Enter a keyword to lookup active content", color = Color.Gray)
            }
        } else if (results.liveChannels.isEmpty() && results.movies.isEmpty() && results.series.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching results found.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (results.liveChannels.isNotEmpty()) {
                    item {
                        Text("Live Channels", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(results.liveChannels) { chan ->
                                val isFav = favorites.any { it.contentId == chan.id && it.contentType == "LIVE" }
                                FocusableItemCard(
                                    title = chan.name,
                                    imageUrl = chan.logoUrl,
                                    subtitle = "Live TV",
                                    isFavorite = isFav,
                                    onFavoriteToggle = if (profile.features.favoritesEnabled) {
                                        {
                                            onToggleFavorite(
                                                FavoriteEntity(
                                                    contentId = chan.id,
                                                    contentType = "LIVE",
                                                    title = chan.name,
                                                    posterOrLogo = chan.logoUrl,
                                                    streamUrl = chan.streamUrl,
                                                    categoryId = chan.categoryId,
                                                    categoryName = chan.categoryName
                                                )
                                            )
                                        }
                                    } else null,
                                    onClick = { onPlayLive(chan) }
                                )
                            }
                        }
                    }
                }
                if (results.movies.isNotEmpty()) {
                    item {
                        Text("Movies Catalog", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(results.movies) { movie ->
                                val isFav = favorites.any { it.contentId == movie.id && it.contentType == "MOVIE" }
                                FocusableItemCard(
                                    title = movie.title,
                                    imageUrl = movie.posterUrl,
                                    subtitle = movie.genre,
                                    isFavorite = isFav,
                                    onFavoriteToggle = if (profile.features.favoritesEnabled) {
                                        {
                                            onToggleFavorite(
                                                FavoriteEntity(
                                                    contentId = movie.id,
                                                    contentType = "MOVIE",
                                                    title = movie.title,
                                                    posterOrLogo = movie.posterUrl,
                                                    streamUrl = movie.streamUrl,
                                                    categoryId = movie.categoryId,
                                                    categoryName = movie.categoryName
                                                )
                                            )
                                        }
                                    } else null,
                                    onClick = { onPlayMovie(movie) }
                                )
                            }
                        }
                    }
                }
                if (results.series.isNotEmpty()) {
                    item {
                        Text("TV Shows & Series", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(results.series) { series ->
                                val isFav = favorites.any { it.contentId == series.id && it.contentType == "SERIES" }
                                FocusableItemCard(
                                    title = series.title,
                                    imageUrl = series.posterUrl,
                                    subtitle = series.genre,
                                    isFavorite = isFav,
                                    onFavoriteToggle = if (profile.features.favoritesEnabled) {
                                        {
                                            onToggleFavorite(
                                                FavoriteEntity(
                                                    contentId = series.id,
                                                    contentType = "SERIES",
                                                    title = series.title,
                                                    posterOrLogo = series.posterUrl,
                                                    streamUrl = "",
                                                    categoryId = series.categoryId,
                                                    categoryName = series.categoryName
                                                )
                                            )
                                        }
                                    } else null,
                                    onClick = { onSeriesClick(series) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Settings Module View ---

@Composable
fun SettingsView(
    profile: com.example.config.ProviderProfile,
    repository: IptvRepository,
    onNavigateToSupport: () -> Unit,
    onNavigateToParental: () -> Unit,
    onLogout: () -> Unit,
    isTv: Boolean,
    onConfigureFootball: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var showProfileDialog by remember { mutableStateOf(false) }
    var subScreen by remember { mutableStateOf<String?>(null) }

    if (subScreen == "CATEGORY_MANAGEMENT") {
        CategoryManagementView(
            profile = profile,
            repository = repository,
            isTv = isTv,
            onBack = { subScreen = null }
        )
    } else {

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Provider Switcher (Demo Only)") },
            text = {
                Column {
                    Text("Select a custom provider profile to instantly reconstruct and customize the player UI and hide disabled features.")
                    Spacer(modifier = Modifier.height(16.dp))
                    ProviderConfigRegistry.ALL_PROFILES.forEach { prof ->
                        Button(
                            onClick = {
                                ProviderConfigRegistry.currentProfile = prof
                                showProfileDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (prof.id == profile.id) Color(profile.branding.primaryColor) else Color.DarkGray
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(prof.name, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) { Text("Close") }
            }
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "SYSTEM SETTINGS",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            // Account Details
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = Color(0xFF334155).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, "Account", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Active Account Connection", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Server: demo.iptvserver.net", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            // Branded Profile Switcher (White Labeling test showcase)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .clickable { showProfileDialog = true }
                    .border(
                        width = 1.dp,
                        color = Color(0xFF334155).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .testTag("provider_switcher_card")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, "White-Label", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("White-Label Provider Profile", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Currently Active: ${profile.name} (Tap to change profiles and see feature-hiding in action)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Category Management Section (Visible if at least one category type is enabled)
        if (profile.features.liveTvEnabled || profile.features.moviesEnabled || profile.features.seriesEnabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .clickable { subScreen = "CATEGORY_MANAGEMENT" }
                        .border(
                            width = 1.dp,
                            color = Color(0xFF334155).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .testTag("category_management_card")
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, "Category Management", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Category Management", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Hide, reorder, or pin Live, Movie, and Series categories", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (profile.features.multiViewEnabled) {
            item {
                val context = LocalContext.current
                val sharedPrefs = remember { context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE) }
                val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager }
                val isLowMemoryDevice = remember { activityManager?.isLowRamDevice == true }
                var maxStreams by remember { mutableStateOf(sharedPrefs.getInt("multi_view_max_streams", if (isLowMemoryDevice) 2 else 4)) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = Color(0xFF334155).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .testTag("multiview_settings_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GridView, "Multi-view Settings", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Multi-view Maximum Streams", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Configure how many parallel feeds you can stream simultaneously (Max 4)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(2, 3, 4).forEach { option ->
                                FilterChip(
                                    selected = maxStreams == option,
                                    onClick = {
                                        maxStreams = option
                                        sharedPrefs.edit().putInt("multi_view_max_streams", option).apply()
                                    },
                                    label = { Text("$option Streams") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(profile.branding.primaryColor),
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("max_streams_chip_$option")
                                )
                            }
                        }
                    }
                }
            }
        }

        if (profile.features.parentalControlEnabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .clickable(onClick = onNavigateToParental)
                        .border(
                            width = 1.dp,
                            color = Color(0xFF334155).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, "Parental Control", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Parental Controls", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Lock/Unlock adult categories and customize PIN codes", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .clickable(onClick = onConfigureFootball)
                    .border(
                        width = 1.dp,
                        color = Color(0xFF334155).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .testTag("football_settings_card")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SportsSoccer,
                        contentDescription = "Football Schedule",
                        tint = Color(profile.branding.primaryColor),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Football Schedule Settings", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Configure show/hide, selected competitions, and teams", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE) }
            var streamFormat by remember { mutableStateOf(sharedPrefs.getString("stream_format", "TS") ?: "TS") }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = Color(0xFF334155).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, "Stream Format", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Live Stream Format", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Switch format if Live streams fail to play (TS vs HLS/M3U8)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TS", "M3U8").forEach { format ->
                            val isSelected = streamFormat == format
                            Button(
                                onClick = {
                                    streamFormat = format
                                    sharedPrefs.edit().putString("stream_format", format).apply()
                                    coroutineScope.launch { repository.refreshEpg() }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(profile.branding.primaryColor) else Color.DarkGray
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (format == "TS") "TS (.ts) (Default)" else "HLS (.m3u8)",
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .clickable {
                        coroutineScope.launch { repository.refreshEpg() }
                    }
                    .border(
                        width = 1.dp,
                        color = Color(0xFF334155).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, "Refresh", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Refresh Cache", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Synchronize playlists, movie grids, series guides, and XMLTV EPG databases", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (profile.features.supportPageEnabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .clickable(onClick = onNavigateToSupport)
                        .border(
                            width = 1.dp,
                            color = Color(0xFF334155).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpCenter, "Support", tint = Color(profile.branding.primaryColor), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Support & Technical FAQ", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Submit bug reports and locate provider contact info", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("logout_button")
            ) {
                Icon(Icons.Default.ExitToApp, "Logout")
                Spacer(modifier = Modifier.width(8.dp))
                Text("LOGOUT SESSION", fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}

@Composable
fun CategoryManagementView(
    profile: com.example.config.ProviderProfile,
    repository: IptvRepository,
    isTv: Boolean,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Determine enabled tabs
    val tabs = remember(profile) {
        buildList {
            if (profile.features.liveTvEnabled) add("LIVE")
            if (profile.features.moviesEnabled) add("MOVIE")
            if (profile.features.seriesEnabled) add("SERIES")
        }
    }
    
    var selectedTab by remember(tabs) { mutableStateOf(tabs.firstOrNull() ?: "LIVE") }
    
    val categories by repository.observeAllCategoriesForManagement(selectedTab)
        .collectAsState(initial = emptyList())
        
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("category_management_back_button")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CATEGORY MANAGEMENT",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    coroutineScope.launch {
                        repository.resetCategoryCustomization(selectedTab)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("reset_categories_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset to Default", style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        // Tab Selection
        if (tabs.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
                containerColor = Color.Transparent,
                contentColor = Color(profile.branding.primaryColor),
                edgePadding = 0.dp,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = when (tab) {
                                    "LIVE" -> "LIVE TV"
                                    "MOVIE" -> "MOVIES"
                                    "SERIES" -> "SERIES"
                                    else -> tab
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }
        }
        
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(profile.branding.primaryColor))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(categories.size, key = { categories[it].id }) { index ->
                    val item = categories[index]
                    var isFocused by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFocused) Color.White.copy(alpha = 0.15f) else Color(profile.branding.surfaceColor)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .border(
                                width = 1.dp,
                                color = if (isFocused) Color.White else Color(0xFF334155).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("category_item_${item.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // Category Name with pin status indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (item.pinned) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pinned",
                                        tint = Color(profile.branding.primaryColor),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    text = item.name,
                                    color = if (item.hidden) Color.Gray else Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (item.hidden) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    fontWeight = if (item.pinned) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            
                            // Reorder: Move Up
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        coroutineScope.launch {
                                            val listIds = categories.map { it.id }.toMutableList()
                                            // Swap the current category ID with the one above it
                                            val temp = listIds[index]
                                            listIds[index] = listIds[index - 1]
                                            listIds[index - 1] = temp
                                            repository.updateCategorySortOrder(selectedTab, listIds)
                                        }
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.testTag("move_up_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) Color.White else Color.Gray
                                )
                            }
                            
                            // Reorder: Move Down
                            IconButton(
                                onClick = {
                                    if (index < categories.size - 1) {
                                        coroutineScope.launch {
                                            val listIds = categories.map { it.id }.toMutableList()
                                            // Swap the current category ID with the one below it
                                            val temp = listIds[index]
                                            listIds[index] = listIds[index + 1]
                                            listIds[index + 1] = temp
                                            repository.updateCategorySortOrder(selectedTab, listIds)
                                        }
                                    }
                                },
                                enabled = index < categories.size - 1,
                                modifier = Modifier.testTag("move_down_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move Down",
                                    tint = if (index < categories.size - 1) Color.White else Color.Gray
                                )
                            }
                            
                            // Pin / Unpin
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.setCategoryPinned(selectedTab, item.id, !item.pinned)
                                    }
                                },
                                modifier = Modifier.testTag("pin_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = if (item.pinned) "Unpin" else "Pin",
                                    tint = if (item.pinned) Color(profile.branding.primaryColor) else Color.White.copy(alpha = 0.5f)
                                )
                            }
                            
                            // Hide / Show Switch
                            Switch(
                                checked = !item.hidden,
                                onCheckedChange = { visible ->
                                    coroutineScope.launch {
                                        repository.setCategoryHidden(selectedTab, item.id, !visible)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(profile.branding.primaryColor),
                                    checkedTrackColor = Color(profile.branding.primaryColor).copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.testTag("hide_switch_${item.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Common UI Components ---

@Composable
fun FocusableItemCard(
    title: String,
    imageUrl: String,
    subtitle: String,
    isLocked: Boolean = false,
    isFavorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(200.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color(0xFF334155).copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, "Locked", tint = Color.Red, modifier = Modifier.size(14.dp))
                }
            }
            if (onFavoriteToggle != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable { onFavoriteToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color.LightGray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FocusableProgressCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    progress: Float,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(130.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color(0xFF334155).copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFF334155),
                modifier = Modifier.fillMaxWidth()
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color.LightGray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatTimestamp(timeMs: Long): String {
    val date = java.util.Date(timeMs)
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailsDialog(
    series: Series,
    repository: IptvRepository,
    profile: com.example.config.ProviderProfile,
    onPlayEpisode: (Series, Episode) -> Unit,
    onDismiss: () -> Unit
) {
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf<Season?>(null) }
    var isLoadingSeasons by remember { mutableStateOf(true) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    LaunchedEffect(series.id) {
        isLoadingSeasons = true
        repository.getSeasons(series.id).collect { fetchedSeasons ->
            seasons = fetchedSeasons
            selectedSeason = fetchedSeasons.firstOrNull()
            isLoadingSeasons = false
        }
    }

    LaunchedEffect(series.id, selectedSeason) {
        val season = selectedSeason
        if (season != null) {
            isLoadingEpisodes = true
            repository.getEpisodes(series.id, season.id).collect { fetchedEpisodes ->
                episodes = fetchedEpisodes
                isLoadingEpisodes = false
            }
        } else {
            episodes = emptyList()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Series Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Scrollable content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Visual Banner & Hero details
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Poster Image
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(165.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.DarkGray)
                            ) {
                                AsyncImage(
                                    model = series.posterUrl,
                                    contentDescription = series.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Details
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = series.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (series.genre.isNotEmpty()) {
                                    Text(
                                        text = series.genre,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(profile.branding.primaryColor),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = series.description.ifEmpty { "No description available for this series." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Seasons list selector
                    item {
                        Column {
                            Text(
                                text = "Seasons",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            if (isLoadingSeasons) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color(profile.branding.primaryColor))
                                }
                            } else if (seasons.isEmpty()) {
                                Text("No seasons found.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(seasons) { s ->
                                        val isSelected = selectedSeason?.id == s.id
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedSeason = s },
                                            label = { Text("Season ${s.seasonNumber}") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(profile.branding.primaryColor),
                                                selectedLabelColor = Color.White,
                                                containerColor = Color.DarkGray,
                                                labelColor = Color.LightGray
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Episodes list section
                    item {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (isLoadingEpisodes) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(profile.branding.primaryColor))
                            }
                        }
                    } else if (episodes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No episodes found in this season.", color = Color.Gray)
                            }
                        }
                    } else {
                        items(episodes) { ep ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlayEpisode(series, ep)
                                        onDismiss()
                                    }
                                    .border(1.dp, Color(0xFF334155).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Number Badge
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(profile.branding.primaryColor).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ep.episodeNumber.toString(),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color(profile.branding.primaryColor),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Episode info
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ep.title.ifEmpty { "Episode ${ep.episodeNumber}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (ep.description.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = ep.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.LightGray,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Play Button
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Episode",
                                        tint = Color(profile.branding.primaryColor),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FootballMatchCard(
    watchMatch: FootballWatchMatch,
    primaryColor: Color,
    surfaceColor: Color,
    onWatchClick: (LiveChannel) -> Unit,
    onAddToMultiView: ((LiveChannel) -> Unit)? = null
) {
    val match = watchMatch.match
    val kickoffMillis = FootballMatchUtils.parseUtcToMillis(match.kickoffUtc)
    val localTime = if (kickoffMillis > 0) {
        val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
        sdf.format(java.util.Date(kickoffMillis))
    } else {
        "Unknown Time"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(280.dp)
            .border(
                width = 1.dp,
                color = Color(0xFF334155).copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("football_match_card_${match.matchId}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top row: Competition details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!match.competitionEmblemUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = match.competitionEmblemUrl,
                            contentDescription = match.competitionName,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sports,
                            contentDescription = "Competition",
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = match.competitionName ?: "Football",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Status Badge
                val statusText = match.status ?: "SCHEDULED"
                Box(
                    modifier = Modifier
                        .background(
                            color = when (statusText) {
                                "LIVE", "IN_PLAY", "PAUSED" -> Color.Red.copy(alpha = 0.15f)
                                "FINISHED" -> Color.Gray.copy(alpha = 0.15f)
                                else -> Color.White.copy(alpha = 0.08f)
                            },
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        color = when (statusText) {
                            "LIVE", "IN_PLAY", "PAUSED" -> Color.Red
                            "FINISHED" -> Color.Gray
                            else -> Color.LightGray
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Teams & Score
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Home Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!match.homeTeamCrestUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = match.homeTeamCrestUrl,
                                contentDescription = match.homeTeamName,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.DarkGray, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = match.homeTeamName?.take(1) ?: "H",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = match.homeTeamName ?: "Home Team",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = match.homeScore?.toString() ?: "-",
                        color = if (match.homeScore != null) Color.White else Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Away Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!match.awayTeamCrestUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = match.awayTeamCrestUrl,
                                contentDescription = match.awayTeamName,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.DarkGray, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = match.awayTeamName?.take(1) ?: "A",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = match.awayTeamName ?: "Away Team",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = match.awayScore?.toString() ?: "-",
                        color = if (match.awayScore != null) Color.White else Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

            // Time and EPG/Watch area
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kickoff",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = localTime,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val matchedChannel = watchMatch.matchedChannel
                if (matchedChannel != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onAddToMultiView != null) {
                            IconButton(
                                onClick = { onAddToMultiView(matchedChannel) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .testTag("football_add_multiview_button_${match.matchId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Add to Multi-view",
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { onWatchClick(matchedChannel) },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("football_watch_button_${match.matchId}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Watch",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Watch",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FootballConfigDialog(
    onDismiss: () -> Unit,
    profile: com.example.config.ProviderProfile,
    repository: IptvRepository,
    isFirstLaunch: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (isFirstLaunch) 1 else 2) }
    
    var showFootballRow by remember { mutableStateOf(repository.footballPrefs.showFootballScheduleOnHome) }
    var footballOptions by remember { mutableStateOf<FootballOptionsResponse?>(null) }
    var isLoadingOptions by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Multi-select states
    var selectedComps by remember { mutableStateOf(repository.footballPrefs.selectedFootballCompetitionCodes.toSet()) }
    var selectedTeams by remember { mutableStateOf(repository.footballPrefs.selectedFootballTeamIds.toSet()) }

    LaunchedEffect(step) {
        if (step == 2 && footballOptions == null) {
            isLoadingOptions = true
            errorMessage = null
            try {
                val response = repository.getFootballOptions(profile.providerId)
                if (response != null) {
                    footballOptions = response
                } else {
                    errorMessage = "Failed to load options from football backend."
                }
            } catch (e: Exception) {
                errorMessage = "Error loading options: ${e.message}"
            } finally {
                isLoadingOptions = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isFirstLaunch) "Football Live Schedule" else "Configure Football Schedule",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                if (step == 1) {
                    Text(
                        text = "Would you like to add a Football Schedule row on your Home Screen?\n\nThis features live match times, scores, and links directly to matched EPG live TV channels for watching games instantly.",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Step 2: Configure options
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFootballRow = !showFootballRow }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = showFootballRow,
                            onCheckedChange = { showFootballRow = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Show Football Row on Home", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Toggle visibility of live matches on homescreen", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (showFootballRow) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (isLoadingOptions) {
                            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(profile.branding.primaryColor))
                            }
                        } else if (errorMessage != null) {
                            Text(errorMessage ?: "Error occurred", color = Color.Red, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val options = footballOptions
                            if (options != null) {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Competitions Section
                                    item {
                                        Text("Competitions", style = MaterialTheme.typography.titleMedium, color = Color(profile.branding.primaryColor), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    
                                    val competitions = options.competitions ?: emptyList()
                                    if (competitions.isEmpty()) {
                                        item {
                                            Text("No competitions available", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                        }
                                    } else {
                                        items(competitions) { comp ->
                                            val isChecked = selectedComps.contains(comp.competitionCode)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedComps = if (isChecked) selectedComps - comp.competitionCode else selectedComps + comp.competitionCode
                                                    }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = {
                                                        selectedComps = if (isChecked) selectedComps - comp.competitionCode else selectedComps + comp.competitionCode
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(comp.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }

                                    // Teams Section
                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Teams", style = MaterialTheme.typography.titleMedium, color = Color(profile.branding.primaryColor), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    val teams = options.teams ?: emptyList()
                                    if (teams.isEmpty()) {
                                        item {
                                            Text("No teams available", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                        }
                                    } else {
                                        items(teams) { team ->
                                            val isChecked = selectedTeams.contains(team.teamId)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedTeams = if (isChecked) selectedTeams - team.teamId else selectedTeams + team.teamId
                                                    }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = {
                                                        selectedTeams = if (isChecked) selectedTeams - team.teamId else selectedTeams + team.teamId
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(team.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) {
                        step = 2
                    } else {
                        // Save changes
                        repository.footballPrefs.showFootballScheduleOnHome = showFootballRow && (selectedComps.isNotEmpty() || selectedTeams.isNotEmpty())
                        repository.footballPrefs.selectedFootballCompetitionCodes = selectedComps.toList()
                        repository.footballPrefs.selectedFootballTeamIds = selectedTeams.toList()
                        repository.footballPrefs.hasSeenFootballSetup = true
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                enabled = step == 1 || !isLoadingOptions
            ) {
                Text(if (step == 1) "Yes, Configure" else "Save & Finish", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (step == 1) {
                        // Opt out
                        repository.footballPrefs.showFootballScheduleOnHome = false
                        repository.footballPrefs.hasSeenFootballSetup = true
                        onDismiss()
                    } else {
                        if (isFirstLaunch) {
                            // On first launch, cancel should at least mark seen
                            repository.footballPrefs.hasSeenFootballSetup = true
                        }
                        onDismiss()
                    }
                }
            ) {
                Text(if (step == 1) "No, Thanks" else "Cancel")
            }
        },
        containerColor = Color(profile.branding.surfaceColor)
    )
}

@Composable
fun UnavailableTmdbItemDialog(
    item: HomeItem,
    profile: com.example.config.ProviderProfile,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Title Info",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Poster & Details Side-by-Side or Column
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Poster
                    if (!item.posterUrl.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.DarkGray)
                        ) {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Metadata
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.title ?: "Untitled",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        val year = (item.releaseDate ?: item.firstAirDate)?.take(4) ?: "Unknown Year"
                        Text(
                            text = "Year: $year",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )

                        if (item.voteAverage != null && item.voteAverage > 0.0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f", item.voteAverage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }

                        val type = if (item.mediaType == "tv") "TV Series" else "Movie"
                        Text(
                            text = "Type: $type",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Overview
                Text(
                    text = item.overview ?: "No overview available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Alert Warning Banner/Message
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF4D4D).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Not Available",
                            tint = Color(0xFFFF4D4D)
                        )
                        Text(
                            text = "This title is not available in your provider library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF4D4D),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

// --- Multi-view Components ---

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SingleTilePlayer(
    channel: LiveChannel,
    isUnmuted: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    onShowMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Instantiate ExoPlayer carefully
    val player = remember(channel.streamUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(context))
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context))
            .build().apply {
                playWhenReady = true
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                val mediaItem = androidx.media3.common.MediaItem.fromUri(channel.streamUrl)
                setMediaItem(mediaItem)
                prepare()
            }
    }

    // Manage volume/unmuted state
    LaunchedEffect(isUnmuted) {
        player.volume = if (isUnmuted) 1.0f else 0.0f
    }

    // Release player on Dispose
    androidx.compose.runtime.DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    // Manage player listeners for loading / error states
    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isLoading = state == androidx.media3.common.Player.STATE_BUFFERING
                isPlaying = state == androidx.media3.common.Player.STATE_READY
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isLoading = false
                errorMessage = "Playback Error"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Border highlighted when focused
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isFocused) 4.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .clickable { onClick() }
            .testTag("multiview_tile_${channel.id}")
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with channel name, mute/unmute indicator, loading/error state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Channel Name Label (top-left)
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .align(Alignment.TopStart)
            )

            // Mute / Unmute Status Icon (top-right)
            Icon(
                imageVector = if (isUnmuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = if (isUnmuted) "Audio Active" else "Muted",
                tint = if (isUnmuted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(2.dp)
            )

            // Loading / Error Indicators (centered)
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "Error",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Quick actions button (bottom-right) or long press indicator
            IconButton(
                onClick = onShowMenu,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Tile Options",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun MultiViewPlayerScreen(
    channels: List<LiveChannel>,
    allChannels: List<LiveChannel>,
    onBack: () -> Unit,
    profile: com.example.config.ProviderProfile
) {
    var activeChannels by remember { mutableStateOf(channels) }
    var focusedIndex by remember { mutableStateOf(0) }
    var showTileMenuForIndex by remember { mutableStateOf<Int?>(null) }
    var showReplaceDialogForIndex by remember { mutableStateOf<Int?>(null) }
    
    // Fullscreen support from tile
    var fullscreenChannel by remember { mutableStateOf<LiveChannel?>(null) }

    if (fullscreenChannel != null) {
        // Play the fullscreen channel in normal player
        com.example.player.IptvPlayer(
            streamUrl = fullscreenChannel!!.streamUrl,
            title = fullscreenChannel!!.name,
            subtitle = fullscreenChannel!!.categoryName,
            isLive = true,
            onBack = { fullscreenChannel = null }
        )
    } else {
        // Tile Options Dialog Menu
        if (showTileMenuForIndex != null) {
            val idx = showTileMenuForIndex!!
            val chan = activeChannels[idx]
            AlertDialog(
                onDismissRequest = { showTileMenuForIndex = null },
                title = { Text("Tile Options: ${chan.name}") },
                text = {
                    Column {
                        Button(
                            onClick = {
                                fullscreenChannel = chan
                                showTileMenuForIndex = null
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                        ) {
                            Icon(Icons.Default.Fullscreen, "Fullscreen")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Go Fullscreen")
                        }

                        Button(
                            onClick = {
                                showReplaceDialogForIndex = idx
                                showTileMenuForIndex = null
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                        ) {
                            Icon(Icons.Default.SwapHoriz, "Replace")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Replace Channel")
                        }

                        if (activeChannels.size > 2) {
                            Button(
                                onClick = {
                                    activeChannels = activeChannels.filterIndexed { i, _ -> i != idx }
                                    focusedIndex = 0
                                    showTileMenuForIndex = null
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Icon(Icons.Default.Delete, "Remove")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Remove Tile")
                            }
                        }

                        val isMuted = focusedIndex != idx
                        Button(
                            onClick = {
                                focusedIndex = idx
                                showTileMenuForIndex = null
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Icon(if (isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "MuteToggle")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isMuted) "Unmute Audio (Focus)" else "Mute Audio")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showTileMenuForIndex = null }) {
                        Text("Close")
                    }
                }
            )
        }

        // Replace Channel Dialog
        if (showReplaceDialogForIndex != null) {
            val idx = showReplaceDialogForIndex!!
            var searchReplaceQuery by remember { mutableStateOf("") }
            val filteredForReplace = remember(allChannels, searchReplaceQuery) {
                allChannels.filter { it.name.contains(searchReplaceQuery, ignoreCase = true) }
            }

            AlertDialog(
                onDismissRequest = { showReplaceDialogForIndex = null },
                title = { Text("Select Replacement Channel") },
                text = {
                    Column(modifier = Modifier.height(300.dp)) {
                        OutlinedTextField(
                            value = searchReplaceQuery,
                            onValueChange = { searchReplaceQuery = it },
                            placeholder = { Text("Search channel...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredForReplace) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newList = activeChannels.toMutableList()
                                            newList[idx] = item
                                            activeChannels = newList
                                            showReplaceDialogForIndex = null
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(item.name, color = Color.White)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showReplaceDialogForIndex = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Main Layout
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Back exit button at top-left (floating overlay)
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .align(Alignment.TopStart)
                    .testTag("exit_multiview_button")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Exit Multi-view", tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                when (activeChannels.size) {
                    2 -> {
                        // 1x2 side-by-side grid
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            activeChannels.forEachIndexed { index, chan ->
                                val isFocused = focusedIndex == index
                                SingleTilePlayer(
                                    channel = chan,
                                    isUnmuted = focusedIndex == index,
                                    isFocused = isFocused,
                                    onClick = { focusedIndex = index },
                                    onShowMenu = { showTileMenuForIndex = index },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    3 -> {
                        // ESPN Style: 1 large left, 2 stacked right
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Large main channel
                            SingleTilePlayer(
                                channel = activeChannels[0],
                                isUnmuted = focusedIndex == 0,
                                isFocused = focusedIndex == 0,
                                onClick = { focusedIndex = 0 },
                                onShowMenu = { showTileMenuForIndex = 0 },
                                modifier = Modifier.weight(1.5f)
                            )
                            
                            // Stacked side channels
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SingleTilePlayer(
                                    channel = activeChannels[1],
                                    isUnmuted = focusedIndex == 1,
                                    isFocused = focusedIndex == 1,
                                    onClick = { focusedIndex = 1 },
                                    onShowMenu = { showTileMenuForIndex = 1 },
                                    modifier = Modifier.weight(1f)
                                )
                                SingleTilePlayer(
                                    channel = activeChannels[2],
                                    isUnmuted = focusedIndex == 2,
                                    isFocused = focusedIndex == 2,
                                    onClick = { focusedIndex = 2 },
                                    onShowMenu = { showTileMenuForIndex = 2 },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    4 -> {
                        // 2x2 grid
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SingleTilePlayer(
                                    channel = activeChannels[0],
                                    isUnmuted = focusedIndex == 0,
                                    isFocused = focusedIndex == 0,
                                    onClick = { focusedIndex = 0 },
                                    onShowMenu = { showTileMenuForIndex = 0 },
                                    modifier = Modifier.weight(1f)
                                )
                                SingleTilePlayer(
                                    channel = activeChannels[1],
                                    isUnmuted = focusedIndex == 1,
                                    isFocused = focusedIndex == 1,
                                    onClick = { focusedIndex = 1 },
                                    onShowMenu = { showTileMenuForIndex = 1 },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SingleTilePlayer(
                                    channel = activeChannels[2],
                                    isUnmuted = focusedIndex == 2,
                                    isFocused = focusedIndex == 2,
                                    onClick = { focusedIndex = 2 },
                                    onShowMenu = { showTileMenuForIndex = 2 },
                                    modifier = Modifier.weight(1f)
                                )
                                SingleTilePlayer(
                                    channel = activeChannels[3],
                                    isUnmuted = focusedIndex == 3,
                                    isFocused = focusedIndex == 3,
                                    onClick = { focusedIndex = 3 },
                                    onShowMenu = { showTileMenuForIndex = 3 },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    else -> {
                        // Fallback single channel
                        if (activeChannels.isNotEmpty()) {
                            SingleTilePlayer(
                                channel = activeChannels[0],
                                isUnmuted = true,
                                isFocused = true,
                                onClick = {},
                                onShowMenu = { showTileMenuForIndex = 0 },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiViewSetupView(
    allChannels: List<LiveChannel>,
    categories: List<Category>,
    pendingMultiViewChannels: List<LiveChannel>,
    profile: com.example.config.ProviderProfile,
    onLaunch: (List<LiveChannel>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE) }
    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager }
    val isLowMemoryDevice = remember { activityManager?.isLowRamDevice == true }
    val maxStreams = remember { sharedPrefs.getInt("multi_view_max_streams", if (isLowMemoryDevice) 2 else 4) }

    var selectedSlots by remember(pendingMultiViewChannels, maxStreams) {
        mutableStateOf(
            List<LiveChannel?>(maxStreams) { index ->
                pendingMultiViewChannels.getOrNull(index)
            }
        )
    }

    var activeSlotIndex by remember {
        mutableStateOf(
            if (pendingMultiViewChannels.isNotEmpty()) {
                val firstEmpty = selectedSlots.indexOfFirst { it == null }
                if (firstEmpty != -1) firstEmpty else 0
            } else 0
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val filteredChannels = remember(allChannels, searchQuery, selectedCategory) {
        allChannels.filter { channel ->
            val matchesCategory = selectedCategory == null || channel.categoryId == selectedCategory
            val matchesSearch = searchQuery.isBlank() || channel.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup Live Multi-view Grid", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(profile.branding.backgroundColor),
                    titleContentColor = Color.White
                ),
                actions = {
                    val validCount = selectedSlots.filterNotNull().size
                    Button(
                        onClick = { onLaunch(selectedSlots.filterNotNull()) },
                        enabled = validCount >= 2,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(profile.branding.primaryColor),
                            disabledContainerColor = Color.Gray
                        ),
                        modifier = Modifier.testTag("launch_multiview_button")
                    ) {
                        Text("Launch Grid ($validCount)")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_multiview_setup_button")) {
                        Text("Cancel", color = Color.LightGray)
                    }
                }
            )
        },
        containerColor = Color(profile.branding.backgroundColor)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Select active slot below, then click any channel in the list to assign it.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Slot Configuration Cards Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (i in 0 until maxStreams) {
                    val channel = selectedSlots[i]
                    val isFocused = activeSlotIndex == i
                    val borderColor = if (isFocused) Color(profile.branding.primaryColor) else Color.DarkGray
                    val borderWidth = if (isFocused) 3.dp else 1.dp
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                            .clickable { activeSlotIndex = i }
                            .testTag("setup_slot_$i"),
                        colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (channel != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = channel.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = channel.categoryName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    TextButton(
                                        onClick = {
                                            val newList = selectedSlots.toMutableList()
                                            newList[i] = null
                                            selectedSlots = newList
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .height(24.dp)
                                            .testTag("setup_clear_slot_$i")
                                    ) {
                                        Text("Clear", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                                    }
                                }
                            } else {
                                Text(
                                    text = "Slot ${i + 1}\n(Empty)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Channel Filter Fields
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter channels for assignment...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Filter") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_filter_channels"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(profile.branding.primaryColor),
                    focusedLabelColor = Color(profile.branding.primaryColor)
                )
            )

            // Categories horizontal list
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All Categories") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(profile.branding.primaryColor),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.id,
                        onClick = { selectedCategory = cat.id },
                        label = { Text(cat.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(profile.branding.primaryColor),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Scrollable Channels Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(130.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredChannels) { channel ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(profile.branding.surfaceColor)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clickable {
                                val newList = selectedSlots.toMutableList()
                                newList[activeSlotIndex] = channel
                                selectedSlots = newList

                                val nextEmpty = newList.indexOfFirst { it == null }
                                if (nextEmpty != -1) {
                                    activeSlotIndex = nextEmpty
                                }
                            }
                            .testTag("setup_channel_card_${channel.id}"),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color.Gray.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (channel.logoUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = channel.logoUrl,
                                    contentDescription = channel.name,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}


