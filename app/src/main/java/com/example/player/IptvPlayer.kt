package com.example.player

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.config.ProviderConfigRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerScaleMode {
    FIT, FILL, STRETCH
}

data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val format: Format,
    val isSelected: Boolean,
    val isSupported: Boolean,
    val label: String
)

@OptIn(UnstableApi::class)
@Composable
fun IptvPlayer(
    streamUrl: String,
    title: String,
    subtitle: String = "",
    isLive: Boolean = false,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    initialPositionMs: Long = 0L,
    onBack: () -> Unit,
    onNextChannel: (() -> Unit)? = null,
    onPrevChannel: (() -> Unit)? = null,
    onReportProblem: (() -> Unit)? = null,
    onAddToMultiView: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPrefs = remember { context.getSharedPreferences("iptv_player_prefs", android.content.Context.MODE_PRIVATE) }

    // Persist and load aspect ratio
    var scaleMode by remember {
        mutableStateOf(
            run {
                val saved = playerPrefs.getString("scale_mode", PlayerScaleMode.FIT.name)
                try {
                    PlayerScaleMode.valueOf(saved ?: PlayerScaleMode.FIT.name)
                } catch (e: Exception) {
                    PlayerScaleMode.FIT
                }
            }
        )
    }

    LaunchedEffect(scaleMode) {
        playerPrefs.edit().putString("scale_mode", scaleMode.name).apply()
    }

    // Persist and load decoder preference (auto, hardware, software)
    var decoderMode by remember {
        mutableStateOf(
            playerPrefs.getString("decoder_mode", "auto") ?: "auto"
        )
    }

    LaunchedEffect(decoderMode) {
        playerPrefs.edit().putString("decoder_mode", decoderMode).apply()
    }

    // ExoPlayer Instance with custom HTTP Data Source and Hardware/Software selection
    val exoPlayer = remember(decoderMode) {
        val customMediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            if (decoderMode == "software") {
                decoders.sortedWith(compareBy { decoder ->
                    val name = decoder.name.lowercase()
                    val isSoftware = name.startsWith("c2.android.") || name.contains("google") || name.contains("sw")
                    if (isSoftware) 0 else 1
                })
            } else if (decoderMode == "hardware") {
                decoders.sortedWith(compareBy { decoder ->
                    val name = decoder.name.lowercase()
                    val isSoftware = name.startsWith("c2.android.") || name.contains("google") || name.contains("sw")
                    if (isSoftware) 1 else 0
                })
            } else {
                decoders
            }
        }

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setMediaCodecSelector(customMediaCodecSelector)
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VisionPlayer/1.0.0 (Android; Mobile)")
            .setAllowCrossProtocolRedirects(true)
        
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    
    // UI HUD Controls visibility
    var showControls by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(0) } // 0: Audio, 1: Subtitles, 2: Aspect Ratio, 3: Decoder

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying, showSettingsDialog) {
        if (showControls && isPlaying && !showSettingsDialog) {
            delay(5000)
            showControls = false
        }
    }

    // Progress updates
    LaunchedEffect(exoPlayer, playbackState) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition
                totalDuration = exoPlayer.duration
                if (totalDuration > 0) {
                    onProgressUpdate(currentPosition, totalDuration)
                }
            }
            delay(1000)
        }
    }

    // Stream URL changes
    LaunchedEffect(streamUrl, exoPlayer) {
        android.util.Log.d("IptvPlayer", "Loading stream uri")
        playbackError = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        
        val mediaItem = MediaItem.fromUri(streamUrl)
        exoPlayer.setMediaItem(mediaItem)
        if (initialPositionMs > 0 && !isLive) {
            exoPlayer.seekTo(initialPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Manage player listeners
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackState = Player.STATE_IDLE
                val redactedHost = com.example.core.redaction.SensitiveDataRedactor.redactHost(streamUrl)
                val redactedMsg = com.example.core.redaction.SensitiveDataRedactor.redactExceptionMessage(error.message)
                
                android.util.Log.e("IptvPlayer", "Failed to play stream for host $redactedHost: $redactedMsg")
                playbackError = "Playback Failed: Media playback error occurred.\nSource Host: $redactedHost"
            }

            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Audio and Subtitle Tracks calculation
    val audioTracks = remember(exoPlayer.currentTracks) {
        val list = mutableListOf<TrackInfo>()
        val tracks = exoPlayer.currentTracks
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    if (group.isTrackSupported(trackIndex)) {
                        val format = group.getTrackFormat(trackIndex)
                        list.add(
                            TrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                format = format,
                                isSelected = group.isTrackSelected(trackIndex),
                                isSupported = true,
                                label = getTrackLabel(format)
                            )
                        )
                    }
                }
            }
        }
        list
    }

    val subtitleTracks = remember(exoPlayer.currentTracks) {
        val list = mutableListOf<TrackInfo>()
        val tracks = exoPlayer.currentTracks
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    if (group.isTrackSupported(trackIndex)) {
                        val format = group.getTrackFormat(trackIndex)
                        list.add(
                            TrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                format = format,
                                isSelected = group.isTrackSelected(trackIndex),
                                isSupported = true,
                                label = getTrackLabel(format)
                            )
                        )
                    }
                }
            }
        }
        list
    }

    val isAudioAutoSelected = remember(audioTracks) { audioTracks.none { it.isSelected } }
    val isSubtitlesDisabled = remember(exoPlayer.trackSelectionParameters) {
        exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }
    val isSubAutoSelected = remember(isSubtitlesDisabled, subtitleTracks) {
        !isSubtitlesDisabled && subtitleTracks.none { it.isSelected }
    }

    // Capture physical remote D-pad keys for Android TV
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    showControls = true
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (showSettingsDialog) {
                                false
                            } else {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!isLive && !showSettingsDialog) {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!isLive && !showSettingsDialog) {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (isLive && onNextChannel != null && !showSettingsDialog) {
                                onNextChannel()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (isLive && onPrevChannel != null && !showSettingsDialog) {
                                onPrevChannel()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (showSettingsDialog) {
                                showSettingsDialog = false
                                true
                            } else if (showControls) {
                                showControls = false
                                true
                            } else {
                                false // Let standard system handle back to exit player
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = null, indication = null) {
                showControls = !showControls
            }
    ) {
        // Video View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = exoPlayer
                    keepScreenOn = true
                    resizeMode = when (scaleMode) {
                        PlayerScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        PlayerScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        PlayerScaleMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                }
            },
            update = { playerView ->
                playerView.resizeMode = when (scaleMode) {
                    PlayerScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    PlayerScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    PlayerScaleMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Branded watermark if configured
        val profile = ProviderConfigRegistry.currentProfile
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = profile.branding.logoText,
                color = Color(profile.branding.primaryColor),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Loading Indicator
        if (playbackState == Player.STATE_BUFFERING) {
            CircularProgressIndicator(
                color = Color(profile.branding.primaryColor),
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
                    .testTag("player_buffering")
            )
        }

        // Error Screen
        playbackError?.let { errorMsg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Playback Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Unable to play this stream",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                playbackError = null
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(profile.branding.primaryColor))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Playback")
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(android.net.Uri.parse(streamUrl), "video/*")
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No external player found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "External Player")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("External Player")
                        }
                        
                        OutlinedButton(
                            onClick = onBack,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Go Back")
                        }
                    }
                    if (onReportProblem != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = onReportProblem,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                        ) {
                            Icon(Icons.Default.ReportProblem, contentDescription = "Report Problem", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Report stream issue to support")
                        }
                    }
                }
            }
        }

        // Custom Overlay HUD
        AnimatedVisibility(
            visible = showControls && playbackError == null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .testTag("player_back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }

                    // Aspect scale, reporting, multi-view and stream properties
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (onAddToMultiView != null && isLive) {
                            IconButton(
                                onClick = onAddToMultiView,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .testTag("player_add_to_multiview_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Add to Multi-view",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings Options",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                scaleMode = when (scaleMode) {
                                    PlayerScaleMode.FIT -> PlayerScaleMode.FILL
                                    PlayerScaleMode.FILL -> PlayerScaleMode.STRETCH
                                    PlayerScaleMode.STRETCH -> PlayerScaleMode.FIT
                                }
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Scale Mode",
                                tint = Color.White
                            )
                        }

                        if (onReportProblem != null) {
                            IconButton(
                                onClick = onReportProblem,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Report,
                                    contentDescription = "Report Problem",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Bottom HUD Control panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Video Progress bar (VOD only)
                    if (!isLive && totalDuration > 0) {
                        Column {
                            Slider(
                                value = currentPosition.toFloat(),
                                onValueChange = { exoPlayer.seekTo(it.toLong()) },
                                valueRange = 0f..totalDuration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(profile.branding.primaryColor),
                                    activeTrackColor = Color(profile.branding.primaryColor),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("player_seek_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(currentPosition),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = formatTime(totalDuration),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else if (isLive) {
                        // Live indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Red, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LIVE PLAYBACK",
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    // Main HUD action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channels navigation (Live only) or Seek Back (VOD)
                        if (isLive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { onPrevChannel?.invoke() },
                                    enabled = onPrevChannel != null,
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Prev Channel", tint = Color.White)
                                }
                                Text(
                                    text = "CHANNELS",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                IconButton(
                                    onClick = { onNextChannel?.invoke() },
                                    enabled = onNextChannel != null,
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Next Channel", tint = Color.White)
                                }
                            }
                        } else {
                            // VOD rewind/fast-forward
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.Default.Replay10, "-10s", tint = Color.White)
                                }
                                IconButton(
                                    onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.Default.Forward10, "+10s", tint = Color.White)
                                }
                            }
                        }

                        // Play / Pause Button (Center)
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(profile.branding.primaryColor), RoundedCornerShape(32.dp))
                                .testTag("player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Options info (Aspect Mode Label or stream resolution)
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "SCALE: $scaleMode",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Settings Dialog overlay
        if (showSettingsDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { showSettingsDialog = false }
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .fillMaxHeight(0.85f)
                        .clickable(enabled = false) {}, // prevent click-through
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Playback & Stream Options",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showSettingsDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Options", tint = Color.White)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Column: Categories
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(end = 16.dp)
                            ) {
                                PlayerSettingsCategoryItem(
                                    text = "Audio Track",
                                    isActive = selectedCategory == 0,
                                    onFocused = { selectedCategory = 0 }
                                )
                                PlayerSettingsCategoryItem(
                                    text = "Subtitles",
                                    isActive = selectedCategory == 1,
                                    onFocused = { selectedCategory = 1 }
                                )
                                PlayerSettingsCategoryItem(
                                    text = "Aspect Ratio",
                                    isActive = selectedCategory == 2,
                                    onFocused = { selectedCategory = 2 }
                                )
                                PlayerSettingsCategoryItem(
                                    text = "Decoder Mode",
                                    isActive = selectedCategory == 3,
                                    onFocused = { selectedCategory = 3 }
                                )
                            }
                            
                            // Divider
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(Color.White.copy(alpha = 0.1f))
                            )
                            
                            // Right Column: Options
                            Column(
                                modifier = Modifier
                                    .weight(2.5f)
                                    .fillMaxHeight()
                                    .padding(start = 16.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    when (selectedCategory) {
                                        0 -> {
                                            item {
                                                PlayerSettingsItem(
                                                    text = "Auto (Default)",
                                                    isSelected = isAudioAutoSelected,
                                                    onClick = {
                                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                            .buildUpon()
                                                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                            .build()
                                                    }
                                                )
                                            }
                                            items(audioTracks) { track ->
                                                PlayerSettingsItem(
                                                    text = track.label,
                                                    isSelected = track.isSelected,
                                                    onClick = {
                                                        val trackGroup = exoPlayer.currentTracks.groups[track.groupIndex].mediaTrackGroup
                                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                            .buildUpon()
                                                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                            .addOverride(TrackSelectionOverride(trackGroup, track.trackIndex))
                                                            .build()
                                                    }
                                                )
                                            }
                                            if (audioTracks.isEmpty()) {
                                                item {
                                                    Text(
                                                        text = "No audio tracks detected",
                                                        color = Color.LightGray,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        1 -> {
                                            item {
                                                PlayerSettingsItem(
                                                    text = "Off",
                                                    isSelected = isSubtitlesDisabled,
                                                    onClick = {
                                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                            .buildUpon()
                                                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                            .build()
                                                    }
                                                )
                                            }
                                            item {
                                                PlayerSettingsItem(
                                                    text = "Auto",
                                                    isSelected = isSubAutoSelected,
                                                    onClick = {
                                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                            .buildUpon()
                                                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                            .build()
                                                    }
                                                )
                                            }
                                            items(subtitleTracks) { track ->
                                                PlayerSettingsItem(
                                                    text = track.label,
                                                    isSelected = track.isSelected,
                                                    onClick = {
                                                        val trackGroup = exoPlayer.currentTracks.groups[track.groupIndex].mediaTrackGroup
                                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                            .buildUpon()
                                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                            .addOverride(TrackSelectionOverride(trackGroup, track.trackIndex))
                                                            .build()
                                                    }
                                                )
                                            }
                                            if (subtitleTracks.isEmpty()) {
                                                item {
                                                    Text(
                                                        text = "No subtitle tracks detected",
                                                        color = Color.LightGray,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        2 -> {
                                            item {
                                                PlayerSettingsItem(
                                                    text = "Fit (Auto Aspect)",
                                                    isSelected = scaleMode == PlayerScaleMode.FIT,
                                                    onClick = { scaleMode = PlayerScaleMode.FIT }
                                                )
                                            }
                                            item {
                                                PlayerSettingsItem(
                                                    text = "Zoom / Fill",
                                                    isSelected = scaleMode == PlayerScaleMode.FILL,
                                                    onClick = { scaleMode = PlayerScaleMode.FILL }
                                                )
                                            }
                                            item {
                                                PlayerSettingsItem(
                                                    text = "Stretch",
                                                    isSelected = scaleMode == PlayerScaleMode.STRETCH,
                                                    onClick = { scaleMode = PlayerScaleMode.STRETCH }
                                                )
                                            }
                                        }
                                        3 -> {
                                            item {
                                                PlayerSettingsItem(
                                                    text = "Auto (Default)",
                                                    isSelected = decoderMode == "auto",
                                                    onClick = { decoderMode = "auto" }
                                                )
                                            }
                                            item {
                                                PlayerSettingsItem(
                                                    text = "Prefer Hardware Acceleration",
                                                    isSelected = decoderMode == "hardware",
                                                    onClick = { decoderMode = "hardware" }
                                                )
                                            }
                                            item {
                                                PlayerSettingsItem(
                                                    text = "Compatibility Mode (Software Decoders)",
                                                    isSelected = decoderMode == "software",
                                                    onClick = { decoderMode = "software" }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerSettingsCategoryItem(
    text: String,
    isActive: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.primary
        isActive -> Color.White.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimary
        isActive -> MaterialTheme.colorScheme.primary
        else -> Color.LightGray
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable { onFocused() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PlayerSettingsItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        isSelected && isFocused -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        isFocused -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected && isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        isSelected -> MaterialTheme.colorScheme.primary
        isFocused -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.White
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun getTrackLabel(format: Format): String {
    val language = format.language ?: "Unknown"
    val label = format.label
    val codecs = format.codecs
    
    val parts = mutableListOf<String>()
    if (!label.isNullOrBlank()) {
        parts.add(label)
    } else if (!language.isNullOrBlank() && language != "und") {
        parts.add(java.util.Locale(language).displayLanguage)
    } else {
        parts.add("Track")
    }
    
    if (format.channelCount > 0) {
        parts.add("${format.channelCount}ch")
    }
    if (!codecs.isNullOrBlank()) {
        parts.add(codecs)
    }
    return parts.joinToString(" - ")
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
