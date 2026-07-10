package com.example.core.network

object NetworkTimeouts {
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 15L
    const val WRITE_TIMEOUT_SECONDS = 15L
    const val CALL_TIMEOUT_SECONDS = 30L

    // Separate longer timeout for football backend requests
    const val FOOTBALL_CALL_TIMEOUT_MS = 15000L
}
