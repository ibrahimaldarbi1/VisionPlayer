package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.config.ProviderConfigRegistry
import com.example.data.*
import com.example.player.IptvPlayer
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlaybackItem(
    val streamUrl: String,
    val title: String,
    val subtitle: String = "",
    val isLive: Boolean = false,
    val contentId: String = "",
    val parentId: String = "",
    val parentTitle: String = "",
    val posterOrLogo: String = "",
    val seasonNum: Int = 0,
    val episodeNum: Int = 0,
    val initialPositionMs: Long = 0L
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize local Room Database
        val database = IptvDatabase.getDatabase(this)
        val repository = IptvRepository(database.iptvDao(), this)

        setContent {
            val currentProfile = ProviderConfigRegistry.currentProfile
            
            // Outer state observer to reconstruct the entire application theme & layouts dynamically on profiles switch!
            var appProfileState by remember { mutableStateOf(currentProfile) }

            // Active Fullscreen Player Item State
            var activePlaybackItem by remember { mutableStateOf<PlaybackItem?>(null) }
            var globalAddToMultiViewChannel by remember { mutableStateOf<LiveChannel?>(null) }
            val coroutineScope = rememberCoroutineScope()

            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    // Navigation Router
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable("splash") {
                            SplashScreen(
                                repository = repository,
                                onNavigateToLogin = {
                                    navController.navigate("login") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                },
                                onNavigateToHome = {
                                    navController.navigate("home") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("login") {
                            LoginScreen(
                                repository = repository,
                                onLoginSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToSupport = {
                                    navController.navigate("support")
                                }
                            )
                        }

                        composable("home") {
                            // Re-trigger configuration profiles check
                            appProfileState = ProviderConfigRegistry.currentProfile

                            HomeScreen(
                                repository = repository,
                                addToMultiViewChannel = globalAddToMultiViewChannel,
                                onAddToMultiViewHandled = { globalAddToMultiViewChannel = null },
                                onPlayLive = { channel ->
                                    // Save Recently Watched for Live channel
                                    coroutineScope.launch {
                                        repository.saveRecentlyWatched(
                                            RecentlyWatchedEntity(
                                                contentId = channel.id,
                                                contentType = "LIVE",
                                                title = channel.name,
                                                posterOrLogo = channel.logoUrl,
                                                streamUrl = channel.streamUrl,
                                                categoryName = channel.categoryName
                                            )
                                        )
                                    }

                                    activePlaybackItem = PlaybackItem(
                                        streamUrl = channel.streamUrl,
                                        title = channel.name,
                                        subtitle = channel.categoryName,
                                        isLive = true,
                                        contentId = channel.id,
                                        posterOrLogo = channel.logoUrl
                                    )
                                },
                                onPlayMovie = { movie ->
                                    // Check if movie progress already exists to resume it
                                    coroutineScope.launch {
                                        val existing = repository.continueWatching.first()
                                        val progress = existing.firstOrNull { it.contentId == movie.id }
                                        val startPos = progress?.positionMs ?: 0L

                                        activePlaybackItem = PlaybackItem(
                                            streamUrl = movie.streamUrl,
                                            title = movie.title,
                                            subtitle = movie.genre,
                                            isLive = false,
                                            contentId = movie.id,
                                            posterOrLogo = movie.posterUrl,
                                            initialPositionMs = startPos
                                        )
                                    }
                                },
                                onPlayEpisode = { series, episode ->
                                    // Check if episode progress already exists to resume it
                                    coroutineScope.launch {
                                        val existing = repository.continueWatching.first()
                                        val progress = existing.firstOrNull { it.contentId == episode.id }
                                        val startPos = progress?.positionMs ?: 0L

                                        activePlaybackItem = PlaybackItem(
                                            streamUrl = episode.streamUrl,
                                            title = episode.title,
                                            subtitle = "${series.title} - S${episode.seasonNumber} E${episode.episodeNumber}",
                                            isLive = false,
                                            contentId = episode.id,
                                            parentId = series.id,
                                            parentTitle = series.title,
                                            posterOrLogo = series.posterUrl,
                                            seasonNum = episode.seasonNumber,
                                            episodeNum = episode.episodeNumber,
                                            initialPositionMs = startPos
                                        )
                                    }
                                },
                                onNavigateToSupport = {
                                    navController.navigate("support")
                                },
                                onNavigateToParental = {
                                    navController.navigate("parental")
                                },
                                onLogout = {
                                    coroutineScope.launch {
                                        repository.logout()
                                        navController.navigate("login") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("support") {
                            SupportScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("parental") {
                            ParentalControlScreen(
                                repository = repository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    // Fullscreen Video Player Overlay Layer
                    activePlaybackItem?.let { item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            IptvPlayer(
                                streamUrl = item.streamUrl,
                                title = item.title,
                                subtitle = item.subtitle,
                                isLive = item.isLive,
                                initialPositionMs = item.initialPositionMs,
                                onAddToMultiView = if (appProfileState.features.multiViewEnabled && item.isLive) {
                                    {
                                        globalAddToMultiViewChannel = LiveChannel(
                                            id = item.contentId,
                                            name = item.title,
                                            streamUrl = item.streamUrl,
                                            logoUrl = item.posterOrLogo,
                                            categoryId = "",
                                            categoryName = item.subtitle,
                                            epgId = "",
                                            channelNumber = 0
                                        )
                                        activePlaybackItem = null // exit solo player!
                                    }
                                } else null,
                                onProgressUpdate = { position, duration ->
                                    // Live progress tracker persistence (Do not save live feeds progress)
                                    if (!item.isLive) {
                                        coroutineScope.launch {
                                            repository.saveContinueWatching(
                                                ContinueWatchingEntity(
                                                    contentId = item.contentId,
                                                    parentId = item.parentId,
                                                    contentType = if (item.parentId.isNotEmpty()) "EPISODE" else "MOVIE",
                                                    title = item.title,
                                                    parentTitle = item.parentTitle,
                                                    posterOrLogo = item.posterOrLogo,
                                                    streamUrl = item.streamUrl,
                                                    positionMs = position,
                                                    durationMs = duration,
                                                    seasonNumber = item.seasonNum,
                                                    episodeNumber = item.episodeNum
                                                )
                                            )
                                        }
                                    }
                                },
                                onBack = {
                                    activePlaybackItem = null
                                },
                                onNextChannel = {
                                    // Channel switching support
                                    coroutineScope.launch {
                                        val session = repository.activeSession.first()
                                        val isDemo = session == null || session.username == "demo_user" || session.username == "demo" || session.serverUrl.contains("demo") || session.serverUrl.isBlank()
                                        val currentChannels = if (!isDemo) {
                                            try {
                                                repository.getLiveChannels(null).first()
                                            } catch (e: Exception) {
                                                IptvMockData.LiveChannels
                                            }
                                        } else {
                                            IptvMockData.LiveChannels
                                        }
                                        val idx = currentChannels.indexOfFirst { it.id == item.contentId }
                                        if (idx != -1 && currentChannels.isNotEmpty()) {
                                            val nextChan = currentChannels[(idx + 1) % currentChannels.size]
                                            activePlaybackItem = PlaybackItem(
                                                streamUrl = nextChan.streamUrl,
                                                title = nextChan.name,
                                                subtitle = nextChan.categoryName,
                                                isLive = true,
                                                contentId = nextChan.id,
                                                posterOrLogo = nextChan.logoUrl
                                            )
                                        }
                                    }
                                },
                                onPrevChannel = {
                                    coroutineScope.launch {
                                        val session = repository.activeSession.first()
                                        val isDemo = session == null || session.username == "demo_user" || session.username == "demo" || session.serverUrl.contains("demo") || session.serverUrl.isBlank()
                                        val currentChannels = if (!isDemo) {
                                            try {
                                                repository.getLiveChannels(null).first()
                                            } catch (e: Exception) {
                                                IptvMockData.LiveChannels
                                            }
                                        } else {
                                            IptvMockData.LiveChannels
                                        }
                                        val idx = currentChannels.indexOfFirst { it.id == item.contentId }
                                        if (idx != -1 && currentChannels.isNotEmpty()) {
                                            val prevChan = currentChannels[(idx - 1 + currentChannels.size) % currentChannels.size]
                                            activePlaybackItem = PlaybackItem(
                                                streamUrl = prevChan.streamUrl,
                                                title = prevChan.name,
                                                subtitle = prevChan.categoryName,
                                                isLive = true,
                                                contentId = prevChan.id,
                                                posterOrLogo = prevChan.logoUrl
                                            )
                                        }
                                    }
                                },
                                onReportProblem = {
                                    activePlaybackItem = null
                                    navController.navigate("support")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
