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

@Composable
fun HomeScreen(
    repository: IptvRepository,
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

    // Initialize content flows
    LaunchedEffect(profile, activeTab, selectedCategoryLive, selectedCategoryMovie, selectedCategorySeries) {
        if (profile.features.liveTvEnabled) {
            categoriesLive = repository.getCategories("LIVE").first()
            liveChannels = repository.getLiveChannels(selectedCategoryLive).first()
        }
        if (profile.features.moviesEnabled) {
            categoriesMovie = repository.getCategories("MOVIE").first()
            moviesList = repository.getMovies(selectedCategoryMovie).first()
        }
        if (profile.features.seriesEnabled) {
            categoriesSeries = repository.getCategories("SERIES").first()
            seriesList = repository.getSeries(selectedCategorySeries).first()
        }
    }

    // Live search executor
    LaunchedEffect(searchQuery, activeTab) {
        if (searchQuery.isNotEmpty()) {
            repository.searchContent(
                searchQuery,
                profile.features.liveTvEnabled,
                profile.features.moviesEnabled,
                profile.features.seriesEnabled
            ).collect { results ->
                searchResult = results
            }
        } else {
            searchResult = SearchResults()
        }
    }

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
                        onTmdbItemClick = { item ->
                            coroutineScope.launch {
                                if (item.mediaType == "tv") {
                                    val matched = repository.findMatchingSeries(item.title ?: "")
                                    if (matched != null) {
                                        activeSeriesDetail = matched
                                    } else {
                                        val dynamicSeries = Series(
                                            id = "dynamic_series_${item.tmdbId ?: java.util.UUID.randomUUID().toString()}",
                                            title = item.title ?: "Untitled Series",
                                            posterUrl = item.posterUrl ?: "",
                                            backdropUrl = item.backdropUrl ?: "",
                                            categoryId = "trending",
                                            categoryName = "Trending",
                                            description = item.overview ?: "No description available.",
                                            year = item.firstAirDate?.take(4) ?: "2024",
                                            genre = "Trending TV",
                                            rating = String.format(java.util.Locale.US, "%.1f", item.voteAverage ?: 0.0)
                                        )
                                        activeSeriesDetail = dynamicSeries
                                    }
                                } else {
                                    val matched = repository.findMatchingMovie(item.title ?: "")
                                    if (matched != null) {
                                        onPlayMovie(matched)
                                    } else {
                                        val dynamicMovie = Movie(
                                            id = "dynamic_movie_${item.tmdbId ?: java.util.UUID.randomUUID().toString()}",
                                            title = item.title ?: "Untitled Movie",
                                            streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                            posterUrl = item.posterUrl ?: "",
                                            backdropUrl = item.backdropUrl ?: "",
                                            categoryId = "trending",
                                            categoryName = "Trending",
                                            description = item.overview ?: "No description available.",
                                            year = item.releaseDate?.take(4) ?: "2024",
                                            duration = "2h 00m",
                                            genre = "Trending Movie",
                                            rating = String.format(java.util.Locale.US, "%.1f", item.voteAverage ?: 0.0)
                                        )
                                        onPlayMovie(dynamicMovie)
                                    }
                                }
                            }
                        }
                    )
                    "LIVE" -> if (profile.features.liveTvEnabled) {
                        LiveChannelsView(
                            channels = liveChannels,
                            categories = categoriesLive,
                            selectedCategory = selectedCategoryLive,
                            onCategorySelected = { selectedCategoryLive = it },
                            onPlayLive = onPlayLive,
                            repository = repository,
                            isTv = isTv,
                            profile = profile,
                            favorites = favorites,
                            onToggleFavorite = toggleFavorite
                        )
                    }
                    "MOVIES" -> if (profile.features.moviesEnabled) {
                        MoviesLibraryView(
                            movies = moviesList,
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
                            seriesList = seriesList,
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
                            channels = liveChannels,
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
                        isTv = isTv
                    )
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
        if (profile.features.searchEnabled) {
            NavigationBarItem(
                selected = activeTab == "SEARCH",
                onClick = { onTabSelected("SEARCH") },
                icon = { Icon(Icons.Default.Search, "Search") },
                label = { Text("Search") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(profile.branding.primaryColor),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_search")
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
            if (profile.features.searchEnabled) {
                TvRailItem(
                    selected = activeTab == "SEARCH",
                    onClick = { onTabSelected("SEARCH") },
                    icon = Icons.Default.Search,
                    label = "Search",
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
    onTmdbItemClick: (HomeItem) -> Unit = {}
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

        // Branded Recently Added Movies Row (Only if Movies enabled)
        if (profile.features.moviesEnabled) {
            item {
                Column {
                    Text(
                        text = "Recently Added Movies",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(IptvMockData.Movies.filter { !it.isAdult }) { movie ->
                            FocusableItemCard(
                                title = movie.title,
                                imageUrl = movie.posterUrl,
                                subtitle = movie.genre,
                                onClick = { onPlayMovie(movie) }
                            )
                        }
                    }
                }
            }
        }

        // Branded Recently Added Series Row (Only if Series enabled)
        if (profile.features.seriesEnabled) {
            item {
                Column {
                    Text(
                        text = "Trending TV Series",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(IptvMockData.SeriesList.filter { !it.isAdult }) { series ->
                            FocusableItemCard(
                                title = series.title,
                                imageUrl = series.posterUrl,
                                subtitle = series.genre,
                                onClick = { onSeriesClick(series) }
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
    onToggleFavorite: (FavoriteEntity) -> Unit = {}
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
    isTv: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    var showProfileDialog by remember { mutableStateOf(false) }

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


