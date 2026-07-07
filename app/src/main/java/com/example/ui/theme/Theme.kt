package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.config.ProviderConfigRegistry

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val profile = ProviderConfigRegistry.currentProfile
    
    // Create a branded Material 3 dark color scheme dynamically from active profile parameters
    val brandedColorScheme = darkColorScheme(
        primary = Color(profile.branding.primaryColor),
        secondary = Color(profile.branding.secondaryColor),
        background = Color(profile.branding.backgroundColor),
        surface = Color(profile.branding.surfaceColor),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = brandedColorScheme,
        typography = Typography,
        content = content
    )
}
