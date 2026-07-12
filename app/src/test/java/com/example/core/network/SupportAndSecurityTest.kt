package com.example.core.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.DemoPolicy
import com.example.config.ProviderConfigRegistry
import com.example.core.redaction.AccountHostHelper
import com.example.core.support.DiagnosticReportBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupportAndSecurityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testDemoPolicyEnforcement() {
        // Since Robolectric unit tests run with custom/debug config, let's verify logic of isDemoSession.
        // It should NEVER authorize blank or arbitrary credentials.
        assertFalse(DemoPolicy.isDemoSession(username = null, serverUrl = null))
        assertFalse(DemoPolicy.isDemoSession(username = "demo", serverUrl = "https://demo.iptvserver.net"))
        assertFalse(DemoPolicy.isDemoSession(username = "demo_user", serverUrl = "https://arbitrary.com"))
        assertFalse(DemoPolicy.isDemoSession(username = "arbitrary", serverUrl = ""))
        
        // Let's verify that even if isDemoModeAllowed is active/inactive, policy is honored.
        val allowed = DemoPolicy.isDemoModeAllowed
        if (!allowed) {
            assertFalse(DemoPolicy.isDemoSession(username = "demo_user", serverUrl = "https://demo.iptvserver.net"))
        } else {
            assertTrue(DemoPolicy.isDemoSession(username = "demo_user", serverUrl = "https://demo.iptvserver.net"))
        }
    }

    @Test
    fun testAccountHostPresentationHelper() {
        // Verify we format raw URLs correctly.
        assertEquals("myprovider.com", AccountHostHelper.formatRedactedHost("http://myprovider.com/player_api.php"))
        assertEquals("myprovider.com", AccountHostHelper.formatRedactedHost("https://myprovider.com:8080/"))
        assertEquals("Not signed in", AccountHostHelper.formatRedactedHost(null))
        assertEquals("Not signed in", AccountHostHelper.formatRedactedHost(""))
        
        // Should not fallback to "demo.iptvserver.net" for empty/null URLs.
        assertNotEquals("demo.iptvserver.net", AccountHostHelper.formatRedactedHost(""))
    }

    @Test
    fun testDiagnosticReportContentAndExclusions() {
        val profile = ProviderConfigRegistry.PREMIUM_PROFILE
        val activeServer = "http://secretuser:secretpass@myprovider.com:8080/player_api.php?username=foo&password=bar"
        
        val report = DiagnosticReportBuilder.buildReport(
            context = context,
            profile = profile,
            activeServerUrl = activeServer,
            subject = "Playback Freeze",
            description = "Channel 101 freeze every 5 seconds",
            isTv = false,
            cacheAgeMs = 120_000L,
            isNetworkConnected = true
        )

        // Must include Provider name and basic fields
        assertTrue(report.contains("App Name: ${profile.appName}"))
        assertTrue(report.contains("Provider ID: ${profile.providerId}"))
        assertTrue(report.contains("Active Account Host: myprovider.com"))
        assertTrue(report.contains("Subject: Playback Freeze"))
        assertTrue(report.contains("Description: Channel 101 freeze every 5 seconds"))
        assertTrue(report.contains("Cache Age: 2 minutes"))
        assertTrue(report.contains("Network Connected: true"))

        // Exclusions: MUST exclude credentials, usernames, passwords, full urls
        assertFalse(report.contains("secretuser"))
        assertFalse(report.contains("secretpass"))
        assertFalse(report.contains("player_api.php"))
        assertFalse(report.contains("username=foo"))
        assertFalse(report.contains("password=bar"))
    }
}
