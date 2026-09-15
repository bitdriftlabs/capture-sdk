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
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.toFields
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

/**
 * Buffers [ILogger] calls made while the SDK is starting, so that calls made between
 * `Capture.Logger.start()` and the completion of [LoggerImpl] initialization aren't lost.
 *
 * Lock-free, since these calls can happen on the main thread and a lock here risks an ANR.
 * Buffered calls are capped at [MAX_BUFFER_BYTES]; once full, new calls are dropped and reported
 * as a single summary log on flush.
 */
internal class PreInitInMemoryLogger(
    private val dateProvider: DateProvider? = null,
) : ILogger {
    private val bufferedCalls = ConcurrentLinkedQueue<BufferedCall>()
    private val bufferedBytes = AtomicInteger(0)
    private val droppedCallCount = AtomicInteger(0)
    private val replayTarget = AtomicReference<IInternalLogger?>()

    // Preserve the non-null ILogger contract while initSdk is still in progress. The outer
    // Capture.Logger getters hide these placeholders by returning null for PreInitInMemoryLogger,
    // so we keep consumers of ILogger expecting non-null values
    override val sessionId: String = UNKNOWN_VALUE
    override val sessionUrl: String = UNKNOWN_VALUE
    override val deviceId: String = UNKNOWN_VALUE
    override val isTracingActive: Boolean = false

    override fun startNewSession() = startNewSession(null)

    override fun startNewSession(sessionId: String?) = add(BufferedCall.StartNewSession(sessionId))

    // Not adding to the buffer as it needs the SDK started. Capture.Logger.createTemporaryDeviceCode
    // handles this case
    override fun createTemporaryDeviceCode(completion: (CaptureResult<String>) -> Unit) =
        completion(CaptureResult.Failure(SdkNotStartedError))

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
            fields = fieldsWithThrowable(fields, throwable),
            occurredAtMs = getTimeStampInMs(),
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
            fields = combineFields(arrayFields, throwableFields(throwable)),
            occurredAtMs = getTimeStampInMs(),
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
        val span = Span(null, name, level, fields?.toFields(), startTimeMs, parentSpanId)
        add(BufferedCall.StartSpan(span, name, fields))
        return span
    }

    override fun log(httpRequestInfo: HttpRequestInfo) = add(BufferedCall.HttpRequest(httpRequestInfo))

    override fun log(httpResponseInfo: HttpResponseInfo) = add(BufferedCall.HttpResponse(httpResponseInfo))

    override fun setSleepMode(sleepMode: SleepMode) = add(BufferedCall.SetSleepMode(sleepMode))

    /**
     * Replays all buffered calls onto [logger] in the order they occurred, then routes any calls
     * made from this point on directly to it. Intended to be called at most once per instance.
     */
    fun flushToNative(logger: IInternalLogger) {
        replayTarget.set(logger)
        drainTo(logger)
    }

    /** Discards any buffered calls without replaying them, e.g. after a failed SDK start. */
    fun clear() {
        bufferedCalls.clear()
        bufferedBytes.set(0)
    }

    private fun getTimeStampInMs(): Long = dateProvider?.invoke()?.time ?: System.currentTimeMillis()

    private fun add(call: BufferedCall) {
        replayTarget.get()?.let {
            call.replay(it)
            return
        }

        if (!tryReserveBytes(call.sizeBytes)) {
            droppedCallCount.incrementAndGet()
            return
        }

        bufferedCalls.add(call)

        // Catches a flushToNative() that ran concurrently right after the check above.
        replayTarget.get()?.let(::drainTo)
    }

    private fun drainTo(logger: IInternalLogger) {
        generateSequence { bufferedCalls.poll() }.forEach { call ->
            bufferedBytes.addAndGet(-call.sizeBytes)
            call.replay(logger)
        }

        val droppedCalls = droppedCallCount.getAndSet(0)
        if (droppedCalls > 0) {
            logger.logInternal(
                type = LogType.INTERNALSDK,
                level = LogLevel.WARNING,
                arrayFields = fieldsOf("dropped_call_count" to droppedCalls.toString()),
            ) {
                "Pre-init logger buffer overflowed while SDK was starting; new buffered calls were dropped"
            }
        }
    }

    /**
     * Reserves [bytes] against the buffer's cap. Optimistically adds the amount first and checks
     * the result, voiding the reservation if it overshot, rather than a compare-and-swap retry
     * loop — the same approach shared-core's `bd-bounded-buffer` uses to size-bound its channel.
     */
    private fun tryReserveBytes(bytes: Int): Boolean {
        if (bufferedBytes.addAndGet(bytes) <= MAX_BUFFER_BYTES) return true

        bufferedBytes.addAndGet(-bytes)
        return false
    }

    private sealed interface BufferedCall {
        val sizeBytes: Int

        fun replay(logger: IInternalLogger)

        data class StartNewSession(
            val sessionId: String?,
        ) : BufferedCall {
            override val sizeBytes = sessionId?.let { sized(it) } ?: OVERHEAD_BYTES

            override fun replay(logger: IInternalLogger) = logger.startNewSession(sessionId)
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
            override val sizeBytes = OVERHEAD_BYTES

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
            override val sizeBytes = OVERHEAD_BYTES

            override fun replay(logger: IInternalLogger) = logger.logAppLaunchTTI(duration)
        }

        data class ScreenView(
            val screenName: String,
        ) : BufferedCall {
            override val sizeBytes = sized(screenName)

            override fun replay(logger: IInternalLogger) = logger.logScreenView(screenName)
        }

        data class StartSpan(
            val span: Span,
            val name: String,
            val fields: Map<String, String>?,
        ) : BufferedCall {
            override val sizeBytes = sized(name) + fields.sizeBytes()

            // Attaches the real logger to the caller's own Span, instead of starting a new one.
            override fun replay(logger: IInternalLogger) = span.attachLogger(logger)
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
            override val sizeBytes = OVERHEAD_BYTES

            override fun replay(logger: IInternalLogger) = logger.setSleepMode(sleepMode)
        }
    }

    private companion object {
        // Worst-case memory footprint of the buffer while the SDK is starting.
        private const val MAX_BUFFER_BYTES = 512 * 1024

        // Each string (message, field key, field value) adds this fixed overhead plus its real
        // UTF-8 byte length.
        private const val OVERHEAD_BYTES = 48
        private const val UNKNOWN_VALUE = "unknown"

        private fun stringBytes(value: String): Int = OVERHEAD_BYTES + value.toByteArray(StandardCharsets.UTF_8).size

        private fun sized(vararg values: String): Int = OVERHEAD_BYTES + values.sumOf(::stringBytes)

        private fun ArrayFields.sizeBytes(): Int = keys.indices.sumOf { stringBytes(keys[it]) + stringBytes(values[it]) }

        private fun Map<String, String>?.sizeBytes(): Int = this?.entries?.sumOf { stringBytes(it.key) + stringBytes(it.value) } ?: 0

        private fun fieldsWithThrowable(
            fields: Map<String, String>?,
            throwable: Throwable?,
        ): ArrayFields = combineFields(fields?.toFields() ?: ArrayFields.EMPTY, throwableFields(throwable))

        private fun throwableFields(throwable: Throwable?): ArrayFields =
            throwable?.let {
                fieldsOf(
                    "_error" to it.javaClass.name.orEmpty(),
                    "_error_details" to it.message.orEmpty(),
                )
            } ?: ArrayFields.EMPTY
    }
}
