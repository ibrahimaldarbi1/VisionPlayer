package com.example.ui.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.config.ProviderProfile
import com.example.data.*
import com.example.ui.feature.common.FocusableItemCard
import com.example.ui.feature.common.FocusableProgressCard
import com.example.ui.feature.football.FootballMatchCard
import com.example.ui.screens.HomeUiState

@Composable
fun HomeDashboardView(
    profile: com.example.config.ProviderProfile,
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
    footballLoadError: String? = null,
    selectedFootballCompetitionKeys: List<String> = emptyList(),
    onRetryFootball: () -> Unit = {},
    onConfigureFootball: () -> Unit = {},
    onAddToMultiView: ((LiveChannel) -> Unit)? = null,
    showFootballScheduleOnHome: Boolean = true
) {
    val filteredFavorites = favorites.filter { fav ->
        when (fav.contentType) {
            "LIVE" -> profile.features.liveTvEnabled
            "MOVIE" -> profile.features.moviesEnabled
            "SERIES" -> profile.features.seriesEnabled
            else -> true
        }
    }

    val filteredContinueWatching = continueWatching.filter { progress ->
        when (progress.contentType) {
            "MOVIE" -> profile.features.moviesEnabled
            "EPISODE", "SERIES" -> profile.features.seriesEnabled
            else -> true
        }
    }

    // Filter dynamic content rows based on white-label feature flags
    val filteredRows = if (homeUiState is HomeUiState.Success) {
        (homeUiState.homeResponse.rows ?: emptyList()).map { row ->
            val items = (row.items ?: emptyList()).filter { item ->
                if (item.mediaType == "tv" && !profile.features.seriesEnabled) false
                else if ((item.mediaType == "movie" || item.mediaType == "movie_library") && !profile.features.moviesEnabled) false
                else true
            }
            row.copy(items = items)
        }.filter { it.items?.isNotEmpty() == true }
    } else {
        emptyList()
    }

    val hasLocalContent = recentlyWatched.isNotEmpty() || filteredContinueWatching.isNotEmpty() || filteredFavorites.isNotEmpty()
    val hasDynamicContent = filteredRows.isNotEmpty()

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
            filteredRows.forEach { row ->
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

        // --- FOOTBALL SCHEDULE ROW (Only if showFootballScheduleOnHome is true) ---
        val showFootballRow = com.example.data.FootballVisibilityHelper.shouldShowFootballFeature(
            featureEnabled = profile.features.footballScheduleEnabled,
            userEnabled = showFootballScheduleOnHome
        )
        val hasSelection = selectedFootballCompetitionKeys.isNotEmpty()

        if (showFootballRow) {
            item {
                Column {
                    Text(
                        text = "Football Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (!hasSelection) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(profile.branding.surfaceColor), RoundedCornerShape(12.dp))
                                .clickable { onConfigureFootball() }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Choose football competitions",
                                color = Color(profile.branding.primaryColor),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isLoadingFootball) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(profile.branding.primaryColor))
                        }
                    } else if (footballLoadError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(profile.branding.surfaceColor), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = footballLoadError ?: "Failed to load football schedule.",
                                    color = Color.Red,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Button(
                                    onClick = onRetryFootball,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                                ) {
                                    Text("Retry", color = Color.White)
                                }
                            }
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
                                text = "No scheduled matches found for your selected competitions.",
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
        if (profile.features.continueWatchingEnabled && filteredContinueWatching.isNotEmpty()) {
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
                        items(filteredContinueWatching) { progress ->
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
        if (profile.features.favoritesEnabled && filteredFavorites.isNotEmpty()) {
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
                        items(filteredFavorites) { fav ->
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
