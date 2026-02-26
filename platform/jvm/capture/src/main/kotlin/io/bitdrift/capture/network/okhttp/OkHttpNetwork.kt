// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.network.ICaptureNetwork
import io.bitdrift.capture.network.ICaptureStream
import io.bitdrift.capture.network.Jni
import io.bitdrift.capture.threading.CaptureDispatchers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.Pipe
import okio.buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// 1024 * 1024 bytes / 1 MiB for the buffer size. This matches the Wire gRPC default:
// https://github.com/square/wire/blob/972df6b3f1e308e38cbfbd6ee0b8d2377aedabdc/wire-library/wire-grpc-client/src/jvmMain/kotlin/com/squareup/wire/internal/grpc.kt#L69
internal const val REQUEST_BODY_BUFFER_SIZE = 1024 * 1024
internal const val NO_DATA = -1
internal const val READ_BUFFER_SIZE = 1024

internal const val APPLICATION_GRPC_CONTENT_TYPE_HEADER_VALUE: String = "application/grpc"
internal const val CONTENT_TYPE_HEADER_KEY: String = "content-type"

// A custom RequestBody type which 1) sets isDuplex() and 2) allows us to create a sink
// which can be used to stream data into the request.
internal class PipeDuplexRequestBody(
    private val contentType: MediaType?,
    pipeMaxBufferSize: Long,
) : RequestBody() {
    private val pipe = Pipe(pipeMaxBufferSize)

    fun createSink() = pipe.sink.buffer()

    override fun contentType() = contentType

    @Suppress("SwallowedException", "EmptyCatchBlock")
    override fun writeTo(sink: BufferedSink) {
        // If this gets called more than once (e.g. due to an automatically injected
        // interceptor), we want to ignore the second call. This is because the pipe
        // is a one-shot object, and we don't want to try to write to it again.
        //
        // From the perspective of the caller this will look like the request was empty.
        try {
            pipe.fold(sink)
        } catch (e: Throwable) {
        }
    }

    override fun isDuplex() = true
}

internal fun newDuplexRequestBody(contentType: MediaType): PipeDuplexRequestBody =
    PipeDuplexRequestBody(
        contentType,
        pipeMaxBufferSize = REQUEST_BODY_BUFFER_SIZE.toLong(),
    )

internal class OkHttpNetwork(
    apiBaseUrl: HttpUrl,
    timeoutSeconds: Long = 2L * 60,
    private val okHttpClient: OkHttpClient,
    private val networkDispatcher: CaptureDispatchers.Network = CaptureDispatchers.Network,
) : ICaptureNetwork {
    private val client: OkHttpClient =
        run {
            val builder = okHttpClient.newBuilder()
            // Certain other libraries will manipulate the bytecode to have the OkHttpClientBuilder
            // constructor automatically add interceptors which tend to not work well with our bespoke
            // client implementation. Remove these extra interceptors here to ensure that we are using
            // a standard client.
            builder.interceptors().clear()
            builder.networkInterceptors().clear()
            builder
                .protocols(
                    if (apiBaseUrl.scheme == "https") {
                        listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
                    } else {
                        listOf(Protocol.H2_PRIOR_KNOWLEDGE)
                    },
                ).writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false) // Retrying messes up the write pipe state management, so disable.
                .build()
        }

    private val url: HttpUrl =
        apiBaseUrl
            .newBuilder()
            .addPathSegments("bitdrift_public.protobuf.client.v1.ApiService/Mux")
            .build()

    override fun startStream(
        streamId: Long,
        headers: Map<String, String>,
    ): ICaptureStream {
        // We call the deallocate call of stream handle N-1 when we create stream handle N, ensuring
        // that we never maintain more than one active stream.
        val streamState = StreamState(streamId, headers)
        activeStream.getAndSet(streamState)?.let { oldStream ->
            oldStream.shutdown()
            oldStream.streamId.deallocate()
        }
        return streamState
    }

    private val activeStream: AtomicReference<StreamState?> = AtomicReference(null)

    // A handle to an active API stream. This holds the streamId which identifies the handle to
    // send upstream events too, and will receive calls via JNI to transmit data over the API
    // stream.
    private inner class StreamState(
        streamId: Long,
        headers: Map<String, String>,
    ) : ICaptureStream {
        val sink: BufferedSink
        val call: Call

        // This provides safe access to the streamId, which is not safe to access after deallocation.
        // Avoiding user-after-free becomes a bit tricky: under presumed invariants, once a new stream
        // is created there should be no further interactions with the old one. As these kind of
        // interactions could result in a use-after-free, we make use of this safe wrapper to noop calls
        // made with the streamId handle after deallocation has happened.
        val streamId = DeallocationGuard(streamId, Jni::releaseApiStream)

        init {
            val contentType = headers[CONTENT_TYPE_HEADER_KEY] ?: APPLICATION_GRPC_CONTENT_TYPE_HEADER_VALUE
            val requestBody = newDuplexRequestBody(contentType.toMediaType())
            val builder =
                Request
                    .Builder()
                    .url(url)
                    .method("POST", requestBody)

            headers.iterator().forEach {
                // PipeDuplexRequestBody takes care of the content-type already.
                if (it.key != CONTENT_TYPE_HEADER_KEY) {
                    builder.addHeader(it.key, it.value)
                }
            }

            sink = requestBody.createSink()
            call = client.newCall(builder.build())

            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        // Since we're using an infinite stream, the stream will always end in some kind
                        // of "failure". This includes things that aren't really failures, like us canceling
                        // the request during shutdown.
                        closeStream(e.toString())
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        // Once we get a response handle, hand it over to the executor for async processing.
                        networkDispatcher.runAsync {
                            try {
                                consumeResponse(response)
                            } finally {
                                response.close()
                            }
                        }
                    }
                },
            )
        }

        override fun sendData(dataToSend: ByteArray) {
            // To send data, we put the data in a buffer and hand it to the request body sink.
            val buffer = Buffer()
            buffer.write(dataToSend)

            // It's possible that a disconnect triggered on the OkHttp executor thread
            // races with outbound traffic, so we have to be defensive here. If this calls fails we
            // don't do anything except log it, as we're expecting the stream to be shut down via the
            // executor thread.
            try {
                sink.write(buffer, dataToSend.size.toLong())
                sink.flush()
            } catch (e: IOException) {
                CaptureJniLibrary.debugError("Failed to write data over API stream: $e")
            }
        }

        override fun shutdown() {
            // This is called when the native end no longer needs this stream, so cancel it if it's
            // still active.
            call.cancel()
            runCatching { sink.close() }
        }

        // Closes the stream unless the stream has already been deallocated.
        private fun closeStream(reason: String) {
            streamId.safeAccess { streamId -> Jni.onApiStreamClosed(streamId, reason) }
        }

        // Handles received stream unless the stream has already been deallocated.
        @Synchronized
        private fun handleReceivedData(
            buffer: ByteArray,
            length: Int,
        ) {
            streamId.safeAccess { streamId -> Jni.onApiChunkReceived(streamId, buffer, length) }
        }

        // Helper function for streaming the response data. This spends most of its time doing
        // a blocking read, and should naturally finish as the stream closes due to source.read
        // throwing an exception indicating the closure reason.
        private fun consumeResponse(response: Response) {
            val source = response.body!!.source()
            var exception: Exception? = null
            @Suppress("TooGenericExceptionCaught")
            try {
                while (true) {
                    val buffer = ByteArray(READ_BUFFER_SIZE)
                    val bytes = source.read(buffer)

                    // Read may return -1 if there is no more data to read. This can happen during shutdown.
                    if (bytes != NO_DATA) {
                        handleReceivedData(buffer, bytes)
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                exception = e
            } finally {
                closeStream(exception?.toString() ?: "closed")
            }
        }
    }
}
