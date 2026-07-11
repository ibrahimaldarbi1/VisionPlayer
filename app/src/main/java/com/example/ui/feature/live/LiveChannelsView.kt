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
import androidx.compose.material.icons.filled.Lock
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChannelsView(
    channels: List<LiveChannel>,
    categories: List<Category>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    isTv: Boolean,
    profile: com.example.config.ProviderProfile,
    favoritesEnabled: Boolean = false,
    favoriteChannelIds: Set<String> = emptySet(),
    favoriteMutationChannelIds: Set<String> = emptySet(),
    favoritesError: String? = null,
    onToggleFavorite: (LiveChannel) -> Unit = {},
    onDismissFavoritesError: () -> Unit = {},
    onStartMultiViewSetup: (() -> Unit)? = null,
    initialLoading: Boolean = false,
    refreshing: Boolean = false,
    channelsError: String? = null,
    onRetryChannels: () -> Unit = {},
    categoriesError: String? = null,
    categoriesLoading: Boolean = false,
    onRetryCategories: () -> Unit = {},

    // Parental Control Parameters
    parentalControlsEnabled: Boolean = false,
    parentalLoading: Boolean = false,
    parentalLoadError: String? = null,
    pinDialogVisible: Boolean = false,
    pinVerificationLoading: Boolean = false,
    pinVerificationError: String? = null,
    lockedLiveCategoryIds: Set<String> = emptySet(),
    parentalSessionUnlocked: Boolean = false,
    pendingParentalChannel: LiveChannel? = null,
    pendingParentalCategoryId: String? = null,
    onChannelSelected: (LiveChannel) -> Unit = {},
    onSubmitParentalPin: (String) -> Unit = {},
    onCancelParentalDialog: () -> Unit = {},
    onRetryParentalStatus: () -> Unit = {},
    onDismissParentalLoadError: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }

    LaunchedEffect(pinDialogVisible) {
        if (!pinDialogVisible) {
            pinInput = ""
        }
    }

    if (parentalControlsEnabled && pinDialogVisible) {
        AlertDialog(
            onDismissRequest = onCancelParentalDialog,
            title = { Text("Parental Control PIN Required") },
            text = {
                Column {
                    val textDesc = when {
                        pendingParentalChannel != null -> "Enter PIN to play ${pendingParentalChannel.name}."
                        pendingParentalCategoryId != null -> {
                            val categoryName = categories.firstOrNull { it.id == pendingParentalCategoryId }?.name ?: ""
                            "Enter PIN to browse category $categoryName."
                        }
                        else -> "Enter PIN to proceed."
                    }
                    Text(textDesc)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = pinInput,
                        onValueChange = { value ->
                            pinInput = value.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("Enter 4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color(profile.branding.primaryColor),
                            focusedLabelColor = Color(profile.branding.primaryColor)
                        ),
                        modifier = Modifier.testTag("parental_pin_input_field")
                    )
                    if (pinVerificationError != null) {
                        Text(
                            text = pinVerificationError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("parental_pin_error_text")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSubmitParentalPin(pinInput) },
                    enabled = !pinVerificationLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                    modifier = Modifier.testTag("confirm_parental_pin_button")
                ) {
                    if (pinVerificationLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Unlock")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelParentalDialog,
                    modifier = Modifier.testTag("cancel_parental_pin_button")
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("parental_pin_dialog")
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

        // Parental Control Status Load Error Banner
        if (parentalLoadError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("parental_error_banner"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = parentalLoadError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    TextButton(
                        onClick = onRetryParentalStatus,
                        modifier = Modifier.testTag("retry_parental_button")
                    ) {
                        Text("Retry")
                    }
                    TextButton(
                        onClick = onDismissParentalLoadError,
                        modifier = Modifier.testTag("dismiss_parental_button")
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }

        if (parentalLoading && parentalControlsEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("parental_loading_banner"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(profile.branding.primaryColor)
                )
                Text(
                    text = "Checking parental controls…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (categoriesError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("categories_error_banner"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Could not update channel categories.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onRetryCategories,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                ) {
                    Text("Retry")
                }
            }
        }

        // Categories horizontal list
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    val showCategoryLock = cat.id in lockedLiveCategoryIds && !parentalSessionUnlocked
                    FilterChip(
                        selected = selectedCategory == cat.id,
                        onClick = { onCategorySelected(cat.id) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cat.name)
                                if (showCategoryLock) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked Category",
                                        modifier = Modifier.size(12.dp).testTag("category_lock_indicator_${cat.id}")
                                    )
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(profile.branding.primaryColor),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (categoriesLoading && categories.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                        .testTag("categories_loading_indicator"),
                    strokeWidth = 2.dp,
                    color = Color(profile.branding.primaryColor)
                )
            }
        }

        // Small non-blocking progress indicator for refreshing
        if (refreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = Color(profile.branding.primaryColor)
            )
        }

        if (favoritesError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("favorites_error_banner"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = favoritesError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismissFavoritesError,
                    modifier = Modifier.testTag("dismiss_favorites_error_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss error")
                }
            }
        }

        // Error with cached channels (existing channels > 0)
        if (channelsError != null && channels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("channels_error_banner"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channelsError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onRetryChannels,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                ) {
                    Text("Retry")
                }
            }
        }

        // Channels Area
        val filteredChannels = remember(channels, searchQuery) {
            if (searchQuery.isBlank()) {
                channels
            } else {
                channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }

        if (initialLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(profile.branding.primaryColor),
                    modifier = Modifier.testTag("channels_loading_indicator")
                )
            }
        } else if (channelsError != null && channels.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = channelsError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("channels_error_message")
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetryChannels,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor)),
                    modifier = Modifier.testTag("retry_channels_button")
                ) {
                    Text("Retry")
                }
            }
        } else if (channels.isEmpty() && searchQuery.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Live TV channels are available.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("no_channels_message")
                )
            }
        } else if (filteredChannels.isEmpty() && searchQuery.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No channels match \"$searchQuery\"",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("no_matching_channels_message")
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
                    val isFav = channel.id in favoriteChannelIds
                    val favoriteMutationInProgress = channel.id in favoriteMutationChannelIds
                    FocusableItemCard(
                        title = channel.name,
                        imageUrl = channel.logoUrl,
                        subtitle = channel.categoryName,
                        isLocked = if (parentalSessionUnlocked) {
                            false
                        } else {
                            channel.isAdult || channel.isLocked || channel.categoryId in lockedLiveCategoryIds
                        },
                        isFavorite = isFav,
                        onFavoriteToggle = if (favoritesEnabled && !favoriteMutationInProgress) {
                            {
                                onToggleFavorite(channel)
                            }
                        } else null,
                        onClick = {
                            onChannelSelected(channel)
                        }
                    )
                }
            }
        }
    }
}
