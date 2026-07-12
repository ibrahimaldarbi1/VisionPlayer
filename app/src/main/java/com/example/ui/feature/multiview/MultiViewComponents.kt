package com.example.ui.feature.multiview

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.config.ProviderProfile
import com.example.data.*

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

    val player = remember(channel.streamUrl) {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(com.example.core.network.NetworkClientFactory.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(context))
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                val mediaItem = androidx.media3.common.MediaItem.fromUri(channel.streamUrl)
                setMediaItem(mediaItem)
                prepare()
            }
    }

    LaunchedEffect(isUnmuted) {
        player.volume = if (isUnmuted) 1.0f else 0.0f
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .align(Alignment.TopStart)
            )

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
    
    var fullscreenChannel by remember { mutableStateOf<LiveChannel?>(null) }

    if (fullscreenChannel != null) {
        com.example.player.IptvPlayer(
            streamUrl = fullscreenChannel!!.streamUrl,
            title = fullscreenChannel!!.name,
            subtitle = fullscreenChannel!!.categoryName,
            isLive = true,
            onBack = { fullscreenChannel = null }
        )
    } else {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
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
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SingleTilePlayer(
                                channel = activeChannels[0],
                                isUnmuted = focusedIndex == 0,
                                isFocused = focusedIndex == 0,
                                onClick = { focusedIndex = 0 },
                                onShowMenu = { showTileMenuForIndex = 0 },
                                modifier = Modifier.weight(1.5f)
                            )
                            
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
