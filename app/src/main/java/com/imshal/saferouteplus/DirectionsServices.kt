package com.imshal.saferouteplus
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsService {

    @GET("maps/api/directions/json")
    fun getRoute(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "walking",
        @Query("alternatives") alternatives: Boolean = true,
        @Query("key") apiKey: String
    ): Call<DirectionsResponse>
}