package com.example.ui.feature.common

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class DeviceType {
    PHONE,
    TV
}

@Composable
fun rememberDeviceType(): DeviceType {
    val context = LocalContext.current
    return remember(context) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            DeviceType.TV
        } else {
            DeviceType.PHONE
        }
    }
}
