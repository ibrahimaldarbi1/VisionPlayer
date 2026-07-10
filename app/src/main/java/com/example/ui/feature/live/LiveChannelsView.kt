package com.example.ui.feature.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.config.ProviderProfile
import com.example.data.*
import com.example.ui.feature.common.FocusableItemCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
