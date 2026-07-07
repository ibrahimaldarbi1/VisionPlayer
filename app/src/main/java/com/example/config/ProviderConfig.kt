package com.example.config

import androidx.compose.ui.graphics.Color

data class ProviderProfile(
    val id: String,
    val name: String,
    val appName: String,
    val features: FeatureConfig,
    val branding: BrandingConfig,
    val support: SupportConfig,
    val layoutMode: LayoutMode = LayoutMode.PREMIUM_STREAMING
)

enum class LayoutMode {
    SIMPLE,
    CLASSIC_IPTV,
    PREMIUM_STREAMING,
    TV_BOX
}

data class FeatureConfig(
    val liveTvEnabled: Boolean = true,
    val moviesEnabled: Boolean = true,
    val seriesEnabled: Boolean = true,
    val epgEnabled: Boolean = true,
    val searchEnabled: Boolean = true,
    val favoritesEnabled: Boolean = true,
    val recentlyWatchedEnabled: Boolean = true,
    val continueWatchingEnabled: Boolean = true,
    val parentalControlEnabled: Boolean = true,
    val supportPageEnabled: Boolean = true,
    val updateCheckerEnabled: Boolean = true,
    val announcementsEnabled: Boolean = true
)

data class BrandingConfig(
    val primaryColor: Long = 0xFF0D6EFD, // Blue
    val secondaryColor: Long = 0xFF6C757D, // Slate
    val backgroundColor: Long = 0xFF121212, // Dark Slate
    val surfaceColor: Long = 0xFF1E1E1E,
    val logoText: String = "WhitePlayer",
    val splashText: String = "Welcome to IPTV Player"
)

data class SupportConfig(
    val email: String = "support@example.com",
    val telegram: String = "t.me/iptvsupport",
    val website: String = "www.example.com",
    val whatsapp: String = "+123456789"
)

object ProviderConfigRegistry {
    // Premium Streaming Profile (All Features Enabled)
    val PREMIUM_PROFILE = ProviderProfile(
        id = "premium_stream",
        name = "Nebula Premium",
        appName = "Nebula TV",
        features = FeatureConfig(
            liveTvEnabled = true,
            moviesEnabled = true,
            seriesEnabled = true,
            epgEnabled = true,
            searchEnabled = true,
            favoritesEnabled = true,
            recentlyWatchedEnabled = true,
            continueWatchingEnabled = true,
            parentalControlEnabled = true,
            supportPageEnabled = true,
            updateCheckerEnabled = true,
            announcementsEnabled = true
        ),
        branding = BrandingConfig(
            primaryColor = 0xFF6366F1, // Indigo-500
            secondaryColor = 0xFF4F46E5, // Indigo-600
            backgroundColor = 0xFF0F0F0F, // Deep charcoal black
            surfaceColor = 0xFF1A1A1A, // Deep dark slate surface
            logoText = "Vision Player",
            splashText = "Premium IPTV"
        ),
        support = SupportConfig(
            email = "support@nebula.tv",
            telegram = "t.me/nebulatv_support",
            website = "www.nebula.tv",
            whatsapp = "+15550199"
        ),
        layoutMode = LayoutMode.PREMIUM_STREAMING
    )

    // Classic IPTV Profile (Live TV + EPG, No VOD)
    val CLASSIC_IPTV_PROFILE = ProviderProfile(
        id = "classic_iptv",
        name = "Retro IPTV",
        appName = "Retro Cable",
        features = FeatureConfig(
            liveTvEnabled = true,
            moviesEnabled = false,
            seriesEnabled = false,
            epgEnabled = true,
            searchEnabled = true,
            favoritesEnabled = true,
            recentlyWatchedEnabled = true,
            continueWatchingEnabled = false,
            parentalControlEnabled = true,
            supportPageEnabled = true,
            updateCheckerEnabled = true,
            announcementsEnabled = true
        ),
        branding = BrandingConfig(
            primaryColor = 0xFF00ADB5, // Cyan Accent
            secondaryColor = 0xFF393E46,
            backgroundColor = 0xFF222831,
            surfaceColor = 0xFF393E46,
            logoText = "RETRO CABLE",
            splashText = "Classic Television Broadcaster"
        ),
        support = SupportConfig(
            email = "support@retrocable.net",
            telegram = "t.me/retrocable",
            website = "www.retrocable.net",
            whatsapp = "+15550188"
        ),
        layoutMode = LayoutMode.CLASSIC_IPTV
    )

    // VOD Only Profile (Movies + Series, No Live TV / No EPG)
    val VOD_ONLY_PROFILE = ProviderProfile(
        id = "vod_only",
        name = "Cosmic VOD",
        appName = "Cosmic Play",
        features = FeatureConfig(
            liveTvEnabled = false,
            moviesEnabled = true,
            seriesEnabled = true,
            epgEnabled = false,
            searchEnabled = true,
            favoritesEnabled = true,
            recentlyWatchedEnabled = false,
            continueWatchingEnabled = true,
            parentalControlEnabled = true,
            supportPageEnabled = true,
            updateCheckerEnabled = true,
            announcementsEnabled = false
        ),
        branding = BrandingConfig(
            primaryColor = 0xFF8A2BE2, // Purple
            secondaryColor = 0xFF4B0082,
            backgroundColor = 0xFF05050A,
            surfaceColor = 0xFF101020,
            logoText = "COSMIC PLAY",
            splashText = "Unlimited VOD Streaming"
        ),
        support = SupportConfig(
            email = "contact@cosmicplay.io",
            telegram = "t.me/cosmicplay",
            website = "www.cosmicplay.io",
            whatsapp = "+15550177"
        ),
        layoutMode = LayoutMode.SIMPLE
    )

    val ALL_PROFILES = listOf(PREMIUM_PROFILE, CLASSIC_IPTV_PROFILE, VOD_ONLY_PROFILE)

    var currentProfile: ProviderProfile = PREMIUM_PROFILE
}
