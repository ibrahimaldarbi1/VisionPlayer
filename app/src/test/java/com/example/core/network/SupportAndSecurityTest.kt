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
        // Centralized DemoPolicy enforcement
        assertFalse(DemoPolicy.isDemoSession(username = null, token = null, serverUrl = null))
        assertFalse(DemoPolicy.isDemoSession(username = "demo", token = "demo_pass", serverUrl = "https://demo.iptvserver.net"))
        assertFalse(DemoPolicy.isDemoSession(username = "demo_user", token = "demo_pass", serverUrl = "https://arbitrary.com"))
        assertFalse(DemoPolicy.isDemoSession(username = "arbitrary", token = "demo_pass", serverUrl = ""))
        
        // Let's verify that even if isDemoModeAllowed is active/inactive, policy is honored.
        val allowed = DemoPolicy.isDemoModeAllowed
        if (!allowed) {
            assertFalse(DemoPolicy.isDemoSession(username = "demo_user", token = "demo_pass", serverUrl = "https://demo.iptvserver.net"))
        } else {
            assertTrue(DemoPolicy.isDemoSession(username = "demo_user", token = "demo_pass", serverUrl = "https://demo.iptvserver.net"))
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

    @Test
    fun testDiagnosticReportDateTimeAndCategory() {
        // 1. Diagnostic date/time and safe error category
        val profile = ProviderConfigRegistry.PREMIUM_PROFILE
        val report = DiagnosticReportBuilder.buildReport(
            context = context,
            profile = profile,
            activeServerUrl = "https://myprovider.com",
            subject = "Login Issue",
            description = "Cannot log in",
            isTv = false,
            cacheAgeMs = 1000L,
            isNetworkConnected = true,
            errorCategory = "AUTHENTICATION_ERROR"
        )
        
        // Assert it contains category
        assertTrue(report.contains("Error Category: AUTHENTICATION_ERROR"))
        // Assert it contains a timestamp (Date/Time: yyyy-MM-dd HH:mm:ss)
        assertTrue(report.contains("Date/Time:") && report.lines().any { it.startsWith("Date/Time:") && it.length > 15 })
    }

    @Test
    fun testSanitizationOfSecretsInDescription() {
        // 2. Sanitization of secrets typed into the support description
        val profile = ProviderConfigRegistry.PREMIUM_PROFILE
        val descriptionWithSecrets = "My password: demo_pass and my private url is http://admin:admin123@mytest.com/login"
        val report = DiagnosticReportBuilder.buildReport(
            context = context,
            profile = profile,
            activeServerUrl = "https://myprovider.com",
            subject = "Leak Test",
            description = descriptionWithSecrets,
            isTv = false,
            cacheAgeMs = 1000L,
            isNetworkConnected = true
        )
        
        // Check that secrets are redacted
        assertFalse(report.contains("admin123"))
        assertFalse(report.contains("demo_pass"))
        assertTrue(report.contains("[redacted]") || report.contains("[REDACTED]") || report.contains("redacted-host"))
    }

    @Test
    fun testContactIntentBuilderEmail() {
        // 3. Email intent recipient, subject and body
        val intent = com.example.core.support.ContactIntentBuilder.buildEmailIntent(
            recipient = "support@provider.com",
            subject = "System Diagnostic Report",
            body = "This is a diagnostic report."
        )
        
        assertNotNull(intent)
        assertEquals(android.content.Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:support%40provider.com?subject=System%20Diagnostic%20Report&body=This%20is%20a%20diagnostic%20report.", intent.data.toString())
    }

    @Test
    fun testContactIntentBuilderTelegram() {
        // 4. Telegram normalization
        val rawHandle = "@my_support_handle"
        val normalized = com.example.core.support.ContactIntentBuilder.normalizeTelegramHandle(rawHandle)
        assertNotNull(normalized)
        assertEquals("my_support_handle", normalized)

        val url = com.example.core.support.ContactIntentBuilder.buildTelegramUrl(normalized!!)
        assertEquals("https://t.me/my_support_handle", url)

        val normalized2 = com.example.core.support.ContactIntentBuilder.normalizeTelegramHandle("t.me/another_handle")
        assertEquals("another_handle", normalized2)

        val normalized3 = com.example.core.support.ContactIntentBuilder.normalizeTelegramHandle("https://telegram.me/web_handle")
        assertEquals("web_handle", normalized3)
    }

    @Test
    fun testContactIntentBuilderWhatsApp() {
        // 5. WhatsApp digits-only normalization and encoded report
        val reportText = "Diagnostic Report Info\nCategory: General"
        val rawNumber = "+1 (555) 019-2834"
        val normalized = com.example.core.support.ContactIntentBuilder.normalizeWhatsAppNumber(rawNumber)
        
        assertNotNull(normalized)
        assertEquals("15550192834", normalized)

        val url = com.example.core.support.ContactIntentBuilder.buildWhatsAppUrl(normalized!!, reportText)
        assertTrue(url.startsWith("https://wa.me/15550192834?text="))
        assertTrue(url.contains("Diagnostic%20Report%20Info"))
    }

    @Test
    fun testSupportUnavailableWhenDisabled() {
        // 9. Support being unavailable when its feature flag is disabled
        val disabledFeatures = com.example.config.FeatureConfig(supportPageEnabled = false)
        assertFalse(com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldShowSupport(disabledFeatures))

        val enabledFeatures = com.example.config.FeatureConfig(supportPageEnabled = true)
        assertTrue(com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldShowSupport(enabledFeatures))
    }

    @Test
    fun testExactDemoCredentialEnforcement() {
        // 7. Exact demo credential enforcement
        val allowed = DemoPolicy.isDemoModeAllowed
        if (allowed) {
            assertTrue(DemoPolicy.isDemoSession("demo_user", "demo_pass", "https://demo.iptvserver.net"))
            assertTrue(DemoPolicy.isDemoSession("demo_user", "demo_pass", "http://demo.iptvserver.net"))
            
            assertFalse(DemoPolicy.isDemoSession("demo_user_extra", "demo_pass", "https://demo.iptvserver.net"))
            assertFalse(DemoPolicy.isDemoSession("demo_user", "demo_pass_extra", "https://demo.iptvserver.net"))
            assertFalse(DemoPolicy.isDemoSession("demo_user", "demo_pass", "https://demo.iptvserver.net/sub"))
            assertFalse(DemoPolicy.isDemoSession("demo", "demo_pass", "https://demo.iptvserver.net"))
            assertFalse(DemoPolicy.isDemoSession("demo_user", "demo", "https://demo.iptvserver.net"))
        }
    }
}
