package com.example.ui.feature.movies

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.config.ProviderProfile
import com.example.data.*
import com.example.ui.feature.common.FocusableItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesLibraryView(
    movies: List<Movie>,
    categories: List<Category>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    isTv: Boolean,
    profile: com.example.config.ProviderProfile,
    favorites: List<FavoriteEntity> = emptyList(),
    onToggleFavorite: (FavoriteEntity) -> Unit = {},
    isLoading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {}
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading && movies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(profile.branding.primaryColor))
                }
            } else if (error != null && movies.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(profile.branding.primaryColor)
                        )
                    ) {
                        Text("Retry", color = Color.White)
                    }
                }
            } else if (movies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No movies available in this category.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (filteredMovies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.fillMaxSize()
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
}
