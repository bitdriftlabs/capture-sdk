// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.retrofit

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.network.HttpField
import io.bitdrift.capture.network.okhttp.OkHttpRequestFieldProvider
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import retrofit2.Invocation
import retrofit2.http.GET
import retrofit2.http.HTTP

@RunWith(MockitoJUnitRunner::class)
class RetrofitUrlPathProviderTest {
    @Mock
    private lateinit var chainedProvider: OkHttpRequestFieldProvider

    @Mock
    private lateinit var request: Request

    @Mock
    private lateinit var invocation: Invocation

    // Test helper annotated interface definitions
    interface TestApiNoAnnotation {
        fun none()
    }

    interface TestApiGetNoSlash {
        @GET("some/path/{id}")
        fun getSome()
    }

    interface TestApiGetWithSlash {
        @GET("/some/path/{id}")
        fun getSomeSlash()
    }

    interface TestApiHttpAnnotation {
        @HTTP(method = "GET", path = "custom/path/{id}")
        fun custom()
    }

    interface TestApiEmptyPath {
        @GET("")
        fun empty()
    }

    private fun method(
        clazz: Class<*>,
        name: String,
    ): java.lang.reflect.Method = clazz.getMethod(name)

    @Test
    fun `provideExtraFields when chained provider is null and no invocation tag then return empty map`() {
        val provider = RetrofitUrlPathProvider(null)
        whenever(request.tag(Invocation::class.java)) doReturn null
        val fields = provider.provideExtraFields(request)
        assertThat(fields).isEmpty()
    }

    @Test
    fun `provideExtraFields when no invocation tag then return chained fields`() {
        val provider = RetrofitUrlPathProvider(chainedProvider)
        whenever(request.tag(Invocation::class.java)) doReturn null
        whenever(chainedProvider.provideExtraFields(request)) doReturn mapOf("chained" to "value")
        val fields = provider.provideExtraFields(request)
        assertThat(fields).isEqualTo(mapOf("chained" to "value"))
    }

    @Test
    fun `provideExtraFields when invocation present but method has no http annotation then return chained fields`() {
        val provider = RetrofitUrlPathProvider(chainedProvider)
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.method()) doReturn method(TestApiNoAnnotation::class.java, "none")
        whenever(chainedProvider.provideExtraFields(request)) doReturn mapOf("chained" to "value")
        val fields = provider.provideExtraFields(request)
        assertThat(fields).isEqualTo(mapOf("chained" to "value"))
    }

    @Test
    fun `provideExtraFields when GET annotation without leading slash then adds leading slash`() {
        val provider = RetrofitUrlPathProvider()
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.method()) doReturn method(TestApiGetNoSlash::class.java, "getSome")
        val fields = provider.provideExtraFields(request)
        assertThat(fields).isEqualTo(mapOf(HttpField.PATH_TEMPLATE to "/some/path/{id}"))
    }

    @Test
    fun `provideExtraFields when GET annotation with leading slash then preserves it`() {
        val provider = RetrofitUrlPathProvider()
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.method()) doReturn method(TestApiGetWithSlash::class.java, "getSomeSlash")
        val fields = provider.provideExtraFields(request)
        assertThat(fields).isEqualTo(mapOf(HttpField.PATH_TEMPLATE to "/some/path/{id}"))
    }

    @Test
    fun `provideExtraFields when HTTP annotation then uses its path`() {
        val provider = RetrofitUrlPathProvider()
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.method()) doReturn method(TestApiHttpAnnotation::class.java, "custom")
        val fields = provider.provideExtraFields(request)
        assertThat(fields).isEqualTo(mapOf(HttpField.PATH_TEMPLATE to "/custom/path/{id}"))
    }

    @Test
    fun `provideExtraFields when empty path returns slash`() {
        val provider = RetrofitUrlPathProvider()
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.method()) doReturn method(TestApiEmptyPath::class.java, "empty")
        val fields = provider.provideExtraFields(request)
        assertThat(fields).isEqualTo(mapOf(HttpField.PATH_TEMPLATE to "/"))
    }

    @Test
    fun `provideExtraFields when annotation present then merge with chained fields`() {
        val provider = RetrofitUrlPathProvider(chainedProvider)
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.method()) doReturn method(TestApiGetNoSlash::class.java, "getSome")
        whenever(chainedProvider.provideExtraFields(request)) doReturn mapOf("chained" to "value")
        val fields = provider.provideExtraFields(request)
        assertThat(fields).isEqualTo(mapOf("chained" to "value", HttpField.PATH_TEMPLATE to "/some/path/{id}"))
    }

    @Test
    fun `provideExtraFields when chained provider supplies path template then retrofit overrides it`() {
        val provider = RetrofitUrlPathProvider(chainedProvider)
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.method()) doReturn method(TestApiHttpAnnotation::class.java, "custom")
        whenever(chainedProvider.provideExtraFields(request)) doReturn
            mapOf(
                HttpField.PATH_TEMPLATE to "/old/path/{id}",
                "chained" to "value",
            )
        val fields = provider.provideExtraFields(request)
        assertThat(fields).isEqualTo(mapOf("chained" to "value", HttpField.PATH_TEMPLATE to "/custom/path/{id}"))
    }
}
