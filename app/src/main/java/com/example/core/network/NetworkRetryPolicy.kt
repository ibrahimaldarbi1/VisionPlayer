package com.example.core.network

import kotlinx.coroutines.delay

object NetworkRetryPolicy {
    
    suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
        factor: Double = 2.0,
        shouldRetry: (Throwable) -> Boolean = { e ->
            if (e is kotlinx.coroutines.CancellationException) {
                false
            } else {
                val error = mapThrowableToNetworkError(e)
                error !is NetworkError.Unauthorized && error !is NetworkError.Unsupported && error !is NetworkError.InvalidResponse
            }
        },
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                if (attempt == maxAttempts || !shouldRetry(e)) {
                    throw e
                }
                val error = mapThrowableToNetworkError(e)
                val safeMsg = error.message
                android.util.Log.w("NetworkRetry", "Attempt $attempt failed. Retrying in ${currentDelay}ms. Error: $safeMsg")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(10000L)
            }
        }
        throw IllegalStateException("Should not reach here")
    }
}
