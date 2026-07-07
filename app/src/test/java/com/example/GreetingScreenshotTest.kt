package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.IptvDatabase
import com.example.data.IptvRepository
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule 
    val composeTestRule = createComposeRule()

    @Test
    fun login_screen_screenshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = IptvDatabase.getDatabase(context)
        val repository = IptvRepository(database.iptvDao(), context)

        composeTestRule.setContent { 
            MyApplicationTheme { 
                LoginScreen(
                    repository = repository,
                    onLoginSuccess = {},
                    onNavigateToSupport = {}
                ) 
            } 
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/login.png")
    }
}
