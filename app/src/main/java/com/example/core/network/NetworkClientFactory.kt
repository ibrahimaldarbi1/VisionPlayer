package com.example.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClientFactory {

    private val userAgentInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestWithUserAgent = originalRequest.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()
        chain.proceed(requestWithUserAgent)
    }

    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(NetworkTimeouts.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkTimeouts.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkTimeouts.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(NetworkTimeouts.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(SafeNetworkLogger)
            .build()
    }
}
