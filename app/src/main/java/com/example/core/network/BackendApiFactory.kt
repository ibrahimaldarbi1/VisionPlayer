package com.example.core.network

import com.example.data.FootballApiService
import com.example.data.HomeApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap

object BackendApiFactory {

    private val homeServices = ConcurrentHashMap<String, HomeApiService>()
    private val footballServices = ConcurrentHashMap<String, FootballApiService>()

    fun getHomeApiService(baseUrl: String, client: OkHttpClient = NetworkClientFactory.sharedClient): HomeApiService {
        val normalizedUrl = baseUrl.removeSuffix("/") + "/"
        return homeServices.getOrPut(normalizedUrl) {
            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
            retrofit.create(HomeApiService::class.java)
        }
    }

    fun getFootballApiService(baseUrl: String, client: OkHttpClient = NetworkClientFactory.footballClient): FootballApiService {
        val normalizedUrl = baseUrl.removeSuffix("/") + "/"
        return footballServices.getOrPut(normalizedUrl) {
            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
            retrofit.create(FootballApiService::class.java)
        }
    }

    // Deprecated direct creation helpers to preserve any direct signatures if needed, but internally uses cached
    fun createHomeApiService(baseUrl: String, client: OkHttpClient = NetworkClientFactory.sharedClient): HomeApiService {
        return getHomeApiService(baseUrl, client)
    }

    fun createFootballApiService(baseUrl: String, client: OkHttpClient = NetworkClientFactory.footballClient): FootballApiService {
        return getFootballApiService(baseUrl, client)
    }
}
