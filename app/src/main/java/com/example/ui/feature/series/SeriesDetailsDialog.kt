package com.example.ui.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.config.ProviderProfile
import com.example.data.*

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
