package com.example.mediastreamer

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val status: String,
    val token: String
)

data class MediaItem(
    val name: String,
    val is_dir: Boolean,
    val has_thumb: Boolean
)

interface ApiService {
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("/api/browse")
    suspend fun browseDirectory(
        @Query("path") path: String,
        @Header("Authorization") token: String
    ): Response<List<MediaItem>>

    companion object {
        fun create(ipAddress: String): ApiService {
            val formattedUrl = if (ipAddress.startsWith("http://") || ipAddress.startsWith("https://")) {
                ipAddress
            } else {
                "http://$ipAddress"
            }
            val baseUrl = if (formattedUrl.endsWith("/")) formattedUrl else "$formattedUrl/"

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}
