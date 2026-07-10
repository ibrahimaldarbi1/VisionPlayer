package com.example.core.network

import com.example.data.FootballApiService
import com.example.data.HomeApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object BackendApiFactory {

    fun createHomeApiService(baseUrl: String, client: OkHttpClient = NetworkClientFactory.sharedClient): HomeApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.removeSuffix("/") + "/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        return retrofit.create(HomeApiService::class.java)
    }

    fun createFootballApiService(baseUrl: String, client: OkHttpClient = NetworkClientFactory.sharedClient): FootballApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.removeSuffix("/") + "/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        return retrofit.create(FootballApiService::class.java)
    }
}
