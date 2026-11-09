// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

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