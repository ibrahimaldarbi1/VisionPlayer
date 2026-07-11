package com.example.ui.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.config.ProviderProfile
import com.example.ui.feature.common.DeviceType

@Composable
fun AppShell(
    profile: ProviderProfile,
    deviceType: DeviceType,
    navigationState: AppNavigationState,
    content: @Composable (AppDestination) -> Unit
) {
    val isTv = deviceType == DeviceType.TV
    val destinations = if (isTv) {
        FeatureAvailabilityPolicy.tvDestinations(profile.features)
    } else {
        FeatureAvailabilityPolicy.phoneDestinations(profile.features)
    }

    if (isTv) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(profile.branding.backgroundColor))
        ) {
            TvAppNavigation(
                activeDestination = navigationState.activeDestination,
                destinations = destinations,
                onDestinationSelected = { navigationState.navigateTo(it, profile.features) },
                profile = profile
            )
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                content(navigationState.activeDestination)
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                PhoneAppNavigation(
                    activeDestination = navigationState.activeDestination,
                    destinations = destinations,
                    onDestinationSelected = { navigationState.navigateTo(it, profile.features) },
                    profile = profile
                )
            },
            containerColor = Color(profile.branding.backgroundColor)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                content(navigationState.activeDestination)
            }
        }
    }
}
