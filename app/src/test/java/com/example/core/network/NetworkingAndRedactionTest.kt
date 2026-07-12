package com.example.core.network

import com.example.core.redaction.SensitiveDataRedactor
import com.example.data.XmltvEpgParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkingAndRedactionTest {

    // --- Redaction Tests ---

    @Test
    fun testRedactingQueryCredentials() {
        val url = "http://myprovider.com/player_api.php?username=myuser&password=mypassword&action=get_live_streams"
        val redactedUrl = SensitiveDataRedactor.redactUrl(url)
        assertEquals("http://myprovider.com", redactedUrl)

        val exceptionMsg = "Failed to connect to http://myprovider.com/player_api.php?username=myuser&password=mypassword"
        val redactedMsg = SensitiveDataRedactor.redactExceptionMessage(exceptionMsg)
        assertFalse(redactedMsg.contains("myuser"))
        assertFalse(redactedMsg.contains("mypassword"))
        assertTrue(redactedMsg.contains("http://myprovider.com"))
    }

    @Test
    fun testRedactingXtreamStreamPaths() {
        val liveStreamUrl = "http://iptv.org:8080/live/user123/pass456/9876.ts"
        val redactedUrl = SensitiveDataRedactor.redactUrl(liveStreamUrl)
        assertEquals("http://iptv.org:8080", redactedUrl)

        val exceptionMsg = "HTTP 404 Error: http://iptv.org:8080/live/user123/pass456/9876.ts"
        val redactedMsg = SensitiveDataRedactor.redactExceptionMessage(exceptionMsg)
        assertFalse(redactedMsg.contains("user123"))
        assertFalse(redactedMsg.contains("pass456"))
        assertFalse(redactedMsg.contains("/9876.ts"))
    }

    @Test
    fun testRedactingMalformedOrEmbeddedUrls() {
        val malformedUrl = "http://invalid-user:pass[word]@myhost.com:9999/live/user/pass/123.ts"
        val redactedUrl = SensitiveDataRedactor.redactUrl(malformedUrl)
        assertFalse(redactedUrl.contains("invalid-user"))
        assertFalse(redactedUrl.contains("pass"))
        assertFalse(redactedUrl.contains("live"))

        val nestedMsg = "Unexpected error inside URL: http://usr:pwd@host.com/xmltv.php?username=u&password=p"
        val redactedMsg = SensitiveDataRedactor.redactExceptionMessage(nestedMsg)
        assertFalse(redactedMsg.contains("usr"))
        assertFalse(redactedMsg.contains("pwd"))
        assertFalse(redactedMsg.contains("xmltv.php"))
    }

    @Test
    fun testRelativeApiRejection() {
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("player_api.php"))
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("xmltv.php"))
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("player_api.php?username=1"))
    }

    @Test
    fun testMalformedInputRejection() {
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("http://my host.com"))
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("some spaces here"))
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("user:pass@host/path"))
    }

    @Test
    fun testPreserveIpv4Ipv6AndDns() {
        assertEquals("192.168.1.100:8080", SensitiveDataRedactor.redactUrl("192.168.1.100:8080"))
        assertEquals("[2001:db8::1]:9000", SensitiveDataRedactor.redactUrl("[2001:db8::1]:9000"))
        assertEquals("myprovider.com", SensitiveDataRedactor.redactUrl("myprovider.com"))
    }

    // --- Error Mapping Tests ---

    @Test
    fun testMappingTimeoutUnauthorizedServerAndConnectivityFailures() {
        val connEx = UnknownHostException("unable to resolve host")
        val mappedConn = mapThrowableToNetworkError(connEx)
        assertTrue(mappedConn is NetworkError.NoConnection)

        val timeoutEx = SocketTimeoutException("connect timed out")
        val mappedTimeout = mapThrowableToNetworkError(timeoutEx)
        assertTrue(mappedTimeout is NetworkError.Timeout)

        val unauthorizedError = mapResponseCodeToNetworkError(401)
        assertTrue(unauthorizedError is NetworkError.Unauthorized)

        val serverError = mapResponseCodeToNetworkError(500)
        assertTrue(serverError is NetworkError.ServerError)
        
        val unsupportedError = mapResponseCodeToNetworkError(501)
        assertTrue(unsupportedError is NetworkError.Unsupported)
    }

    @Test
    fun testUnknownSafety() {
        val mapped = mapThrowableToNetworkError(RuntimeException("leaked DB password credentials"))
        assertTrue(mapped is NetworkError.Unknown)
        assertEquals("An unexpected network error occurred.", (mapped as NetworkError.Unknown).message)
    }

    // --- Retry Behavior Tests ---

    @Test
    fun testRetryRefusingAuthenticationFailures() = runTest {
        var attempts = 0
        try {
            NetworkRetryPolicy.retryWithBackoff(maxAttempts = 3) {
                attempts++
                throw NetworkError.Unauthorized
            }
            fail("Expected NetworkError.Unauthorized to be thrown")
        } catch (e: NetworkError.Unauthorized) {
            // Success
        }
        assertEquals(1, attempts)
    }

    @Test
    fun testRetryRefusalForTerminalErrorsAndCancellation() = runTest {
        var attempts = 0
        try {
            NetworkRetryPolicy.retryWithBackoff(maxAttempts = 3) {
                attempts++
                throw NetworkError.InvalidResponse
            }
            fail("Expected InvalidResponse")
        } catch (e: NetworkError.InvalidResponse) {
            // Success
        }
        assertEquals(1, attempts)

        attempts = 0
        try {
            NetworkRetryPolicy.retryWithBackoff(maxAttempts = 3) {
                attempts++
                throw kotlinx.coroutines.CancellationException("Cancelled")
            }
            fail("Expected CancellationException")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Success
        }
        assertEquals(1, attempts)
    }

    @Test
    fun testRetryRemainingBoundedForSafeRequests() = runTest {
        var attempts = 0
        try {
            NetworkRetryPolicy.retryWithBackoff(maxAttempts = 3) {
                attempts++
                throw SocketTimeoutException("Timed out")
            }
            fail("Expected SocketTimeoutException to be thrown")
        } catch (e: SocketTimeoutException) {
            // Success
        }
        assertEquals(3, attempts)
    }

    // --- XMLTV Parsing / Streaming ---

    @Test
    fun testXmltvStreamingAndParsing() {
        val fakeXml = """
            <tv>
                <programme start="20260712080000 +0000" stop="20260712090000 +0000" channel="chan1">
                    <title>Live Football Match</title>
                    <desc>Premium Live Match Coverage</desc>
                </programme>
            </tv>
        """.trimIndent()
        
        val programs = XmltvEpgParser.parseXmltv(fakeXml)
        assertEquals(1, programs.size)
        assertEquals("chan1", programs[0].channelId)
        assertEquals("Live Football Match", programs[0].title)
        assertEquals("Premium Live Match Coverage", programs[0].description)
    }

    @Test
    fun testMalformedXmltvThrowsInvalidResponse() = runTest {
        val malformedXml = "completely invalid non-xml content"
        try {
            XmltvEpgParser.parseXmltvStrict(java.io.StringReader(malformedXml))
            fail("Expected exception on malformed XML lacking tv root")
        } catch (e: Exception) {
            // Success
        }

        val badStructure = "<tv><programme start=\""
        try {
            XmltvEpgParser.parseXmltvStrict(java.io.StringReader(badStructure))
            fail("Expected exception on unclosed attribute")
        } catch (e: Exception) {
            // Success
        }
    }

    @Test
    fun testValidEmptyXmltvSucceeds() = runTest {
        val emptyXml = "<tv></tv>"
        val programs = XmltvEpgParser.parseXmltvStrict(java.io.StringReader(emptyXml))
        assertTrue(programs.isEmpty())
    }

    @Test
    fun testBracketedIpv6RedactHost() {
        assertEquals("[2001:db8::1]", SensitiveDataRedactor.redactHost("[2001:db8::1]"))
        assertEquals("[2001:db8::1]", SensitiveDataRedactor.redactHost("[2001:db8::1]:9000"))
        assertEquals("[2001:db8::1]", SensitiveDataRedactor.redactHost("http://[2001:db8::1]:9000/path?param=val"))
    }

    @Test
    fun testPortValidationInRedactUrl() {
        assertEquals("[2001:db8::1]:65535", SensitiveDataRedactor.redactUrl("[2001:db8::1]:65535"))
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("[2001:db8::1]:65536"))
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("[2001:db8::1]:0"))
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("[2001:db8::1]:-1"))
        assertEquals("redacted-host", SensitiveDataRedactor.redactUrl("[2001:db8::1]:abc"))
    }

    @Test
    fun testMedia3UserAgentMatchesConstant() {
        assertEquals("VisionPlayer/1.0.0 (Android; Mobile)", NetworkClientFactory.USER_AGENT)
    }
}
