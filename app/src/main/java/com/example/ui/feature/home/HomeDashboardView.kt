package com.example.ui.feature.home

import androidx.compose.foundation.layout.*
import com.example.ui.screens.HomeUiState
import com.example.ui.feature.common.FocusableItemCard
import com.example.ui.feature.common.FocusableProgressCard
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.config.ProviderProfile
import com.example.data.*
import com.example.ui.feature.football.FootballMatchCard

@Composable
fun HomeDashboardView(
    profile: ProviderProfile,
    favorites: List<FavoriteEntity>,
    continueWatching: List<ContinueWatchingEntity>,
    recentlyWatched: List<RecentlyWatchedEntity>,
    onPlayLive: (LiveChannel) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onPlayEpisode: (Series, Episode) -> Unit,
    onSeriesClick: (Series) -> Unit,
    isTv: Boolean,
    onTabSelected: (String) -> Unit,
    onToggleFavorite: ((FavoriteEntity) -> Unit)? = null,
    homeUiState: HomeUiState? = null,
    onTmdbItemClick: ((HomeItem) -> Unit)? = null,
    footballMatches: List<FootballWatchMatch>? = null,
    isLoadingFootball: Boolean = false,
    footballLoadError: String? = null,
    selectedFootballCompetitionKeys: List<String>? = null,
    onRetryFootball: (() -> Unit)? = null,
    onConfigureFootball: (() -> Unit)? = null,
    onAddToMultiView: ((LiveChannel) -> Unit)? = null,
    showFootballScheduleOnHome: Boolean = false
) {
    val hasLocalContent = recentlyWatched.isNotEmpty() || continueWatching.isNotEmpty() || favorites.isNotEmpty() || showFootballScheduleOnHome
    val hasDynamicContent = homeUiState is HomeUiState.Success && !homeUiState.homeResponse.rows.isNullOrEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        if (!hasLocalContent && !hasDynamicContent && homeUiState !is HomeUiState.Loading) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No content available", color = Color.Gray)
                }
            }
        }

        if (homeUiState is HomeUiState.Success) {
            items(homeUiState.homeResponse.rows.orEmpty()) { row ->
                if (!row.items.isNullOrEmpty()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = row.title ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(row.items.orEmpty()) { item ->
                                FocusableItemCard(
                                    title = item.title ?: "",
                                    imageUrl = item.posterUrl ?: item.backdropUrl ?: "",
                                    subtitle = "",
                                    onClick = { onTmdbItemClick?.invoke(item) }
                                )
                            }
                        }
                    }
                }
            }
        } else if (homeUiState is HomeUiState.Loading) {
            item {
                Box(modifier = Modifier.fillParentMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        val showFootballRow = com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldShowFootball(profile.features) && showFootballScheduleOnHome
        val hasSelection = !selectedFootballCompetitionKeys.isNullOrEmpty()

        if (showFootballRow) {
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Football Schedule",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = { onConfigureFootball?.invoke() }) {
                            Text("Configure")
                        }
                    }
                    if (!hasSelection) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("Select competitions in Settings to view schedule.", color = Color.Gray)
                        }
                    } else if (isLoadingFootball) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (footballLoadError != null) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(footballLoadError, color = Color.Red)
                                Button(onClick = { onRetryFootball?.invoke() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    } else if (footballMatches.isNullOrEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("No scheduled matches found for your selected competitions.", color = Color.Gray)
                        }
                    } else {
                        val context = LocalContext.current
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(footballMatches) { match ->
                                FootballMatchCard(
                                    watchMatch = match,
primaryColor = MaterialTheme.colorScheme.primary,
surfaceColor = MaterialTheme.colorScheme.surfaceVariant,
                                    onWatchClick = { channel ->
                                        try {
                                            onPlayLive(channel)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Cannot open matched IPTV channel: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onAddToMultiView = onAddToMultiView ?: {}
                                )
                            }
                        }
                    }
                }
            }
        }

        if (profile.features.liveTvEnabled && profile.features.recentlyWatchedEnabled && recentlyWatched.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Recently Watched Channels",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                title = item.title ?: "",
                                imageUrl = item.posterOrLogo,
                                subtitle = "Live Stream",
                                onClick = { onPlayLive(channel) }
                            )
                        }
                    }
                }
            }
        }

        if (profile.features.continueWatchingEnabled && continueWatching.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Continue Watching",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(continueWatching) { progress ->
                            FocusableProgressCard(
                                title = progress.title,
                                imageUrl = progress.posterOrLogo,
subtitle = "",
                                progress = 0.5f,
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

        if (profile.features.favoritesEnabled && favorites.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "My Saved Favorites",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(favorites) { fav ->
                            FocusableItemCard(
                                title = fav.title,
                                imageUrl = fav.posterOrLogo,
                                subtitle = fav.contentType,
                                isFavorite = true,
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
