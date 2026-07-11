package com.example.ui.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.config.ProviderProfile

@Composable
fun TvAppNavigation(
    activeDestination: AppDestination,
    destinations: List<AppDestination>,
    onDestinationSelected: (AppDestination) -> Unit,
    profile: ProviderProfile
) {
    NavigationRail(
        containerColor = Color(profile.branding.surfaceColor),
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp)) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "App Logo",
                    tint = Color(profile.branding.primaryColor),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = profile.branding.logoText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (destinations.contains(AppDestination.HOME)) {
                TvRailItem(
                    selected = activeDestination == AppDestination.HOME,
                    onClick = { onDestinationSelected(AppDestination.HOME) },
                    icon = Icons.Default.Home,
                    label = "Home",
                    profile = profile
                )
            }
            if (destinations.contains(AppDestination.LIVE)) {
                TvRailItem(
                    selected = activeDestination == AppDestination.LIVE,
                    onClick = { onDestinationSelected(AppDestination.LIVE) },
                    icon = Icons.Default.Tv,
                    label = "Live TV",
                    profile = profile
                )
            }
            if (destinations.contains(AppDestination.EPG)) {
                TvRailItem(
                    selected = activeDestination == AppDestination.EPG,
                    onClick = { onDestinationSelected(AppDestination.EPG) },
                    icon = Icons.Default.CalendarMonth,
                    label = "Guide",
                    profile = profile
                )
            }
            if (destinations.contains(AppDestination.MOVIES)) {
                TvRailItem(
                    selected = activeDestination == AppDestination.MOVIES,
                    onClick = { onDestinationSelected(AppDestination.MOVIES) },
                    icon = Icons.Default.Movie,
                    label = "Movies",
                    profile = profile
                )
            }
            if (destinations.contains(AppDestination.SERIES)) {
                TvRailItem(
                    selected = activeDestination == AppDestination.SERIES,
                    onClick = { onDestinationSelected(AppDestination.SERIES) },
                    icon = Icons.Default.VideoLibrary,
                    label = "Series",
                    profile = profile
                )
            }
            if (destinations.contains(AppDestination.SETTINGS)) {
                TvRailItem(
                    selected = activeDestination == AppDestination.SETTINGS,
                    onClick = { onDestinationSelected(AppDestination.SETTINGS) },
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    profile = profile
                )
            }
        }
    }
}

@Composable
fun TvRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    profile: ProviderProfile
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> Color(profile.branding.primaryColor)
                    isFocused -> Color.White.copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected || isFocused) Color.White else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected || isFocused) Color.White else Color.Gray,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
