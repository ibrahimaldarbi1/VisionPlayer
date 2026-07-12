package com.example.ui.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.config.ProviderProfile
import com.example.data.*
import com.example.ui.feature.common.FocusableItemCard

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
    onToggleFavorite: (FavoriteEntity) -> Unit = {},
    isLoading: Boolean = false,
    error: String? = null
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
        } else if (isLoading && results.liveChannels.isEmpty() && results.movies.isEmpty() && results.series.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(profile.branding.primaryColor))
            }
        } else if (error != null && results.liveChannels.isEmpty() && results.movies.isEmpty() && results.series.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
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
