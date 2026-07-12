package com.example.core.network

import com.example.core.redaction.SensitiveDataRedactor
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
        // Redacted URL must keep only scheme and host, removing credentials, query, path, and userInfo
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

    // --- Error Mapping Tests ---

    @Test
    fun testMappingTimeoutUnauthorizedServerAndConnectivityFailures() {
        // Connectivity mapping
        val connEx = UnknownHostException("unable to resolve host")
        val mappedConn = mapThrowableToNetworkError(connEx)
        assertTrue(mappedConn is NetworkError.NoConnection)

        // Timeout mapping
        val timeoutEx = SocketTimeoutException("connect timed out")
        val mappedTimeout = mapThrowableToNetworkError(timeoutEx)
        assertTrue(mappedTimeout is NetworkError.Timeout)

        // Status code mapping
        val unauthorizedError = mapResponseCodeToNetworkError(401)
        assertTrue(unauthorizedError is NetworkError.Unauthorized)

        val serverError = mapResponseCodeToNetworkError(500)
        assertTrue(serverError is NetworkError.ServerError)
        
        val unsupportedError = mapResponseCodeToNetworkError(501)
        assertTrue(unsupportedError is NetworkError.Unsupported)
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
        assertEquals(1, attempts) // Should not retry on Unauthorized
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
        assertEquals(3, attempts) // Should attempt exactly 3 times (bounded)
    }
}
