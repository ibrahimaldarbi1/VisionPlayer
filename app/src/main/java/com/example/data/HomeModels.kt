package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class HomeResponse(
    @Json(name = "providerId") val providerId: String?,
    @Json(name = "rows") val rows: List<HomeRow>?
)

@JsonClass(generateAdapter = true)
data class HomeRow(
    @Json(name = "id") val id: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "items") val items: List<HomeItem>?
)

@JsonClass(generateAdapter = true)
data class HomeItem(
    @Json(name = "rank") val rank: Int?,
    @Json(name = "tmdbId") val tmdbId: Int?,
    @Json(name = "mediaType") val mediaType: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "overview") val overview: String?,
    @Json(name = "posterUrl") val posterUrl: String?,
    @Json(name = "backdropUrl") val backdropUrl: String?,
    @Json(name = "releaseDate") val releaseDate: String?,
    @Json(name = "firstAirDate") val firstAirDate: String?,
    @Json(name = "voteAverage") val voteAverage: Double?,
    @Json(name = "popularity") val popularity: Double?
)

interface HomeApiService {
    @GET("api/v1/home")
    suspend fun getHome(
        @Query("provider_id") providerId: String
    ): HomeResponse
}

class HomeApiClient(private val baseUrl: String) {
    val service: HomeApiService = com.example.core.network.BackendApiFactory.getHomeApiService(baseUrl)

    suspend fun getHome(providerId: String): HomeResponse {
        return service.getHome(providerId)
    }
}
