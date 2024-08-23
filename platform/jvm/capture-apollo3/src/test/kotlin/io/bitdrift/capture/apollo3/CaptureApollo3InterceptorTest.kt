// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.apollo3

import com.apollographql.apollo3.api.Adapter
import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.CompiledField
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.json.JsonWriter
import com.apollographql.apollo3.interceptor.ApolloInterceptorChain
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import kotlinx.coroutines.flow.flow
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class CaptureApollo3InterceptorTest {
    private val logger: ILogger = mock()
    private val captureInterceptor = CaptureApollo3Interceptor(logger)

    @Test
    fun `interceptor logs`() {
        // ARRANGE
        val query = TestQuery()
        val request = ApolloRequest.Builder(query).build()
        val chain: ApolloInterceptorChain = mock()

        whenever(chain.proceed(request)).thenReturn(emitResponse())

        // ACT
        captureInterceptor.intercept(request, chain)

        // ASSERT
        verify(logger).startSpan("_graphql", LogLevel.DEBUG, mapOf("_operation_type" to "query", "_operation_name" to "TestQuery"))
    }

    private fun emitResponse() = flow {
        emit(
            ApolloResponse.Builder(
                TestQuery(),
                UUID.randomUUID(),
                TestData(true)
            ).build()
        )
    }

    class TestQuery : Query<TestData> {
        override fun adapter(): Adapter<TestData> = TODO("Not yet implemented")
        override fun document(): String = "TestDocument"
        override fun id(): String = "TestId"
        override fun name(): String = "TestQuery"
        override fun rootField(): CompiledField = TODO("Not yet implemented")
        override fun serializeVariables(writer: JsonWriter, customScalarAdapters: CustomScalarAdapters) = Unit
    }

    data class TestData(val testVar: Boolean) : Query.Data
}

