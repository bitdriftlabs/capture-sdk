// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.extractFields
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.providers.toThrowableFields
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

internal class PreInitInMemoryLogger : ILogger {
    private val bufferedCalls = ConcurrentLinkedQueue<BufferedCall>()
    private val bufferedBytes = AtomicInteger(0)
    private val droppedCallCount = AtomicInteger(0)

    // Preserve the non-null ILogger contract while initSdk is still in progress. The outer
    // Capture.Logger getters hide these placeholders by returning null for PreInitInMemoryLogger,
    // So we keep consumers of ILogger expecting a non-null values
    override val sessionId: String = UNKNOWN_VALUE
    override val sessionUrl: String = UNKNOWN_VALUE
    override val deviceId: String = UNKNOWN_VALUE
    override val isTracingActive: Boolean = false

    override fun startNewSession() = add(BufferedCall.StartNewSession)

    override fun createTemporaryDeviceCode(completion: (CaptureResult<String>) -> Unit) =
        add(BufferedCall.CreateTemporaryDeviceCode(completion))

    override fun addField(
        key: String,
        value: String,
    ) = add(BufferedCall.AddField(key, value))

    override fun removeField(key: String) = add(BufferedCall.RemoveField(key))

    override fun setFeatureFlagExposure(
        name: String,
        variant: String,
    ) = add(BufferedCall.StringFeatureFlagExposure(name, variant))

    override fun setEntityId(entityId: String) = add(BufferedCall.SetEntityId(entityId))

    override fun clearEntityId() = add(BufferedCall.ClearEntityId)

    override fun setFeatureFlagExposure(
        name: String,
        variant: Boolean,
    ) = add(BufferedCall.BooleanFeatureFlagExposure(name, variant))

    override fun log(
        level: LogLevel,
        fields: Map<String, String>?,
        throwable: Throwable?,
        message: () -> String,
    ) = add(
        BufferedCall.Log(
            level = level,
            fields = extractFields(fields, throwable),
            occurredAtMs = System.currentTimeMillis(),
            message = message(),
        ),
    )

    override fun log(
        level: LogLevel,
        arrayFields: ArrayFields,
        throwable: Throwable?,
        message: () -> String,
    ) = add(
        BufferedCall.Log(
            level = level,
            fields = combineFields(arrayFields, throwable.toThrowableFields()),
            occurredAtMs = System.currentTimeMillis(),
            message = message(),
        ),
    )

    override fun logAppLaunchTTI(duration: Duration) = add(BufferedCall.AppLaunchTti(duration))

    override fun logScreenView(screenName: String) = add(BufferedCall.ScreenView(screenName))

    override fun startSpan(
        name: String,
        level: LogLevel,
        fields: Map<String, String>?,
        startTimeMs: Long?,
        parentSpanId: UUID?,
    ): Span {
        add(BufferedCall.StartSpan(name, level, fields?.toMap(), startTimeMs, parentSpanId))
        return Span(null, name, level, fields?.toFields(), startTimeMs, parentSpanId)
    }

    override fun log(httpRequestInfo: HttpRequestInfo) = add(BufferedCall.HttpRequest(httpRequestInfo))

    override fun log(httpResponseInfo: HttpResponseInfo) = add(BufferedCall.HttpResponse(httpResponseInfo))

    override fun setSleepMode(sleepMode: SleepMode) = add(BufferedCall.SetSleepMode(sleepMode))

    fun flushToNative(loggerImpl: LoggerImpl) {
        generateSequence { bufferedCalls.poll() }.forEach { call ->
            bufferedBytes.addAndGet(-call.sizeBytes)
            call.replay(loggerImpl)
        }

        val droppedCalls = droppedCallCount.getAndSet(0)
        if (droppedCalls > 0) {
            loggerImpl.logInternal(
                type = LogType.INTERNALSDK,
                level = LogLevel.WARNING,
                arrayFields = fieldsOf("dropped_call_count" to droppedCalls.toString()),
            ) {
                "Pre-init logger buffer overflowed while SDK was starting; new buffered calls were dropped"
            }
        }
    }

    fun clear() {
        bufferedCalls.clear()
        bufferedBytes.set(0)
    }

    private fun add(call: BufferedCall) {
        if (!tryReserveBytes(call.sizeBytes)) {
            droppedCallCount.incrementAndGet()
            return
        }

        bufferedCalls.add(call)
    }

    private fun tryReserveBytes(bytes: Int): Boolean {
        do {
            val currentBytes = bufferedBytes.get()
            if (bytes > MAX_BUFFER_BYTES - currentBytes) return false
        } while (!bufferedBytes.compareAndSet(currentBytes, currentBytes + bytes))

        return true
    }

    private sealed interface BufferedCall {
        val sizeBytes: Int

        fun replay(logger: IInternalLogger)

        data object StartNewSession : BufferedCall {
            override val sizeBytes = CALL_OVERHEAD_BYTES

            override fun replay(logger: IInternalLogger) = logger.startNewSession()
        }

        data class CreateTemporaryDeviceCode(
            val completion: (CaptureResult<String>) -> Unit,
        ) : BufferedCall {
            override val sizeBytes = CALL_OVERHEAD_BYTES

            override fun replay(logger: IInternalLogger) = logger.createTemporaryDeviceCode(completion)
        }

        data class AddField(
            val key: String,
            val value: String,
        ) : BufferedCall {
            override val sizeBytes = sized(key, value)

            override fun replay(logger: IInternalLogger) = logger.addField(key, value)
        }

        data class RemoveField(
            val key: String,
        ) : BufferedCall {
            override val sizeBytes = sized(key)

            override fun replay(logger: IInternalLogger) = logger.removeField(key)
        }

        data class StringFeatureFlagExposure(
            val name: String,
            val variant: String,
        ) : BufferedCall {
            override val sizeBytes = sized(name, variant)

            override fun replay(logger: IInternalLogger) = logger.setFeatureFlagExposure(name, variant)
        }

        data class BooleanFeatureFlagExposure(
            val name: String,
            val variant: Boolean,
        ) : BufferedCall {
            override val sizeBytes = sized(name)

            override fun replay(logger: IInternalLogger) = logger.setFeatureFlagExposure(name, variant)
        }

        data class SetEntityId(
            val entityId: String,
        ) : BufferedCall {
            override val sizeBytes = sized(entityId)

            override fun replay(logger: IInternalLogger) = logger.setEntityId(entityId)
        }

        data object ClearEntityId : BufferedCall {
            override val sizeBytes = CALL_OVERHEAD_BYTES

            override fun replay(logger: IInternalLogger) = logger.clearEntityId()
        }

        data class Log(
            val level: LogLevel,
            val fields: ArrayFields,
            val occurredAtMs: Long,
            val message: String,
        ) : BufferedCall {
            override val sizeBytes = sized(message) + fields.sizeBytes()

            override fun replay(logger: IInternalLogger) {
                logger.logInternal(
                    type = LogType.NORMAL,
                    level = level,
                    arrayFields = fields,
                    matchingArrayFields = ArrayFields.EMPTY,
                    attributesOverrides = LogAttributesOverrides.OccurredAt(occurredAtMs),
                    blocking = false,
                ) { message }
            }
        }

        data class AppLaunchTti(
            val duration: Duration,
        ) : BufferedCall {
            override val sizeBytes = CALL_OVERHEAD_BYTES

            override fun replay(logger: IInternalLogger) = logger.logAppLaunchTTI(duration)
        }

        data class ScreenView(
            val screenName: String,
        ) : BufferedCall {
            override val sizeBytes = sized(screenName)

            override fun replay(logger: IInternalLogger) = logger.logScreenView(screenName)
        }

        data class StartSpan(
            val name: String,
            val level: LogLevel,
            val fields: Map<String, String>?,
            val startTimeMs: Long?,
            val parentSpanId: UUID?,
        ) : BufferedCall {
            override val sizeBytes = sized(name) + fields.sizeBytes()

            override fun replay(logger: IInternalLogger) {
                logger.startSpan(name, level, fields, startTimeMs, parentSpanId)
            }
        }

        data class HttpRequest(
            val request: HttpRequestInfo,
        ) : BufferedCall {
            override val sizeBytes = sized(request.toString())

            override fun replay(logger: IInternalLogger) = logger.log(request)
        }

        data class HttpResponse(
            val response: HttpResponseInfo,
        ) : BufferedCall {
            override val sizeBytes = sized(response.toString())

            override fun replay(logger: IInternalLogger) = logger.log(response)
        }

        data class SetSleepMode(
            val sleepMode: SleepMode,
        ) : BufferedCall {
            override val sizeBytes = CALL_OVERHEAD_BYTES

            override fun replay(logger: IInternalLogger) = logger.setSleepMode(sleepMode)
        }
    }

    private companion object {
        private const val MAX_BUFFER_BYTES = 512 * 1024
        private const val CALL_OVERHEAD_BYTES = 64
        private const val UNKNOWN_VALUE = "unknown"

        private fun sized(vararg values: String): Int = CALL_OVERHEAD_BYTES + values.sumOf { it.toByteArray(StandardCharsets.UTF_8).size }

        private fun ArrayFields.sizeBytes(): Int = keys.indices.sumOf { sized(keys[it], values[it]) }

        private fun Map<String, String>?.sizeBytes(): Int = this?.entries?.sumOf { sized(it.key, it.value) } ?: 0
    }
}
