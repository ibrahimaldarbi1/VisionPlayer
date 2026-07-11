package com.example.ui.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.config.FeatureConfig

@Stable
class AppNavigationState(
    initialDestination: AppDestination = AppDestination.HOME
) {
    var activeDestination by mutableStateOf(initialDestination)
        private set

    fun navigateTo(
        destination: AppDestination,
        features: FeatureConfig
    ) {
        activeDestination = FeatureAvailabilityPolicy.sanitizeDestination(
            requested = destination,
            features = features
        )
    }

    fun onFeaturesChanged(
        features: FeatureConfig
    ) {
        activeDestination = FeatureAvailabilityPolicy.sanitizeDestination(
            requested = activeDestination,
            features = features
        )
    }
}

@Composable
fun rememberAppNavigationState(
    initialDestination: AppDestination = AppDestination.HOME
): AppNavigationState {
    return rememberSaveable(
        saver = Saver(
            save = { it.activeDestination.name },
            restore = { 
                AppNavigationState(
                    AppDestination.fromKey(it) ?: AppDestination.HOME
                ) 
            }
        )
    ) {
        AppNavigationState(initialDestination)
    }
}
