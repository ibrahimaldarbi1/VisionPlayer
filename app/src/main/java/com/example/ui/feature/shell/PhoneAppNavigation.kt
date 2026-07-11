package com.example.ui.feature.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.example.config.ProviderProfile

@Composable
fun PhoneAppNavigation(
    activeDestination: AppDestination,
    destinations: List<AppDestination>,
    onDestinationSelected: (AppDestination) -> Unit,
    profile: ProviderProfile
) {
    NavigationBar(
        containerColor = Color(profile.branding.surfaceColor),
        contentColor = Color.White
    ) {
        if (destinations.contains(AppDestination.HOME)) {
            NavigationBarItem(
                selected = activeDestination == AppDestination.HOME,
                onClick = { onDestinationSelected(AppDestination.HOME) },
                icon = { Icon(Icons.Default.Home, "Home") },
                label = { Text("Home") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(profile.branding.primaryColor),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_home")
            )
        }
        if (destinations.contains(AppDestination.LIVE)) {
            NavigationBarItem(
                selected = activeDestination == AppDestination.LIVE,
                onClick = { onDestinationSelected(AppDestination.LIVE) },
                icon = { Icon(Icons.Default.Tv, "Live") },
                label = { Text("Live") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(profile.branding.primaryColor),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_live")
            )
        }
        if (destinations.contains(AppDestination.MOVIES)) {
            NavigationBarItem(
                selected = activeDestination == AppDestination.MOVIES,
                onClick = { onDestinationSelected(AppDestination.MOVIES) },
                icon = { Icon(Icons.Default.Movie, "Movies") },
                label = { Text("Movies") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(profile.branding.primaryColor),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_movies")
            )
        }
        if (destinations.contains(AppDestination.SERIES)) {
            NavigationBarItem(
                selected = activeDestination == AppDestination.SERIES,
                onClick = { onDestinationSelected(AppDestination.SERIES) },
                icon = { Icon(Icons.Default.VideoLibrary, "Series") },
                label = { Text("Series") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(profile.branding.primaryColor),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_series")
            )
        }
        if (destinations.contains(AppDestination.SETTINGS)) {
            NavigationBarItem(
                selected = activeDestination == AppDestination.SETTINGS,
                onClick = { onDestinationSelected(AppDestination.SETTINGS) },
                icon = { Icon(Icons.Default.Settings, "Settings") },
                label = { Text("More") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(profile.branding.primaryColor),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_settings")
            )
        }
    }
}
