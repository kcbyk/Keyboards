package com.example.musickeyboard.api

import com.example.musickeyboard.model.InstantResponse
import com.example.musickeyboard.model.SearchApiResponse
import com.example.musickeyboard.model.StatusResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicApiService {

    // Test ettiğimiz gerçek endpointler
    // BASE_URL = https://mp3-apisi.onrender.com/

    @GET("api/v1/instant")
    suspend fun instantConvert(
        @Query("q") query: String,
        @Query("key") apiKey: String
    ): InstantResponse

    @GET("api/v1/status/{job_id}")
    suspend fun checkStatus(
        @Path("job_id") jobId: String,
        @Query("key") apiKey: String
    ): StatusResponse

    @GET("api/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("key") apiKey: String
    ): SearchApiResponse
}
