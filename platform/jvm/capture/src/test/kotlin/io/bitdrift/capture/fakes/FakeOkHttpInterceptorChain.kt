// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

class FakeOkHttpInterceptorChain(
    private val originalRequest: Request,
) : Interceptor.Chain {
    var capturedRequest: Request? = null

    override fun request(): Request = originalRequest

    override fun proceed(request: Request): Response {
        capturedRequest = request
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
    }

    override fun connection() = null

    override fun call() = throw UnsupportedOperationException()

    override fun connectTimeoutMillis(): Int = 0

    override fun withConnectTimeout(
        timeout: Int,
        unit: java.util.concurrent.TimeUnit,
    ): Interceptor.Chain = this

    override fun readTimeoutMillis(): Int = 0

    override fun withReadTimeout(
        timeout: Int,
        unit: java.util.concurrent.TimeUnit,
    ): Interceptor.Chain = this

    override fun writeTimeoutMillis(): Int = 0

    override fun withWriteTimeout(
        timeout: Int,
        unit: java.util.concurrent.TimeUnit,
    ): Interceptor.Chain = this
}
