package io.bitdrift.gradletestapp.data.service

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

public interface BinaryJazzRetrofitService {
    @GET("wp-json/genrenator/v1/genre/{count}")
    suspend fun generateGenres(@Path("count") count: Int?): Response<List<String>>

    @GET("wp-json/genrenator/v1/story/{count}")
    suspend fun generateStories(@Path("count") count: Int?): Response<List<String>>
}