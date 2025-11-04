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

@RunWith(MockitoJUnitRunner::class)
class RetrofitUrlPathProviderTest {
    @Mock
    private lateinit var chainedProvider: OkHttpRequestFieldProvider

    @Mock
    private lateinit var request: Request

    @Mock
    private lateinit var invocation: Invocation

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
    fun `provideExtraFields when invocation tag present but annotationUrl is null then return chained fields`() {
        val provider = RetrofitUrlPathProvider(chainedProvider)
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.annotationUrl()) doReturn null
        whenever(chainedProvider.provideExtraFields(request)) doReturn mapOf("chained" to "value")

        val fields = provider.provideExtraFields(request)

        assertThat(fields).isEqualTo(mapOf("chained" to "value"))
    }

    @Test
    fun `provideExtraFields when annotationUrl is present then return path template`() {
        val provider = RetrofitUrlPathProvider()
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.annotationUrl()) doReturn "some/path/{id}"

        val fields = provider.provideExtraFields(request)

        assertThat(fields).isEqualTo(mapOf(HttpField.PATH_TEMPLATE to "/some/path/{id}"))
    }

    @Test
    fun `provideExtraFields when annotationUrl has leading slash then return path template`() {
        val provider = RetrofitUrlPathProvider()
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.annotationUrl()) doReturn "/some/path/{id}"

        val fields = provider.provideExtraFields(request)

        assertThat(fields).isEqualTo(mapOf(HttpField.PATH_TEMPLATE to "/some/path/{id}"))
    }

    @Test
    fun `provideExtraFields when annotationUrl is present then merge with chained fields`() {
        val provider = RetrofitUrlPathProvider(chainedProvider)
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.annotationUrl()) doReturn "some/path/{id}"
        whenever(chainedProvider.provideExtraFields(request)) doReturn mapOf("chained" to "value")

        val fields = provider.provideExtraFields(request)

        assertThat(fields).isEqualTo(
            mapOf(
                "chained" to "value",
                HttpField.PATH_TEMPLATE to "/some/path/{id}",
            ),
        )
    }

    @Test
    fun `provideExtraFields when chained provider is null and annotationUrl is present then return path template`() {
        val provider = RetrofitUrlPathProvider(null)
        whenever(request.tag(Invocation::class.java)) doReturn invocation
        whenever(invocation.annotationUrl()) doReturn "some/path/{id}"

        val fields = provider.provideExtraFields(request)

        assertThat(fields).isEqualTo(mapOf(HttpField.PATH_TEMPLATE to "/some/path/{id}"))
    }
}
