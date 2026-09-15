// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.events.performance.MemoryPressureLevel
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.toFields
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

/**
 * Buffers [IInternalLogger] calls made before [LoggerImpl] exists, so nothing is lost while
 * the SDK is starting.
 */
internal class PreInitInMemoryLogger(
    private val dateProvider: DateProvider? = null,
) : IInternalLogger {
    private val bufferedCalls = ConcurrentLinkedQueue<BufferedCall>()
    private val bufferedBytes = AtomicInteger(0)
    private val droppedCallCount = AtomicInteger(0)
    private val drainTarget = AtomicReference<IInternalLogger?>()
    private val failed = AtomicBoolean(false)

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
    ): Span = Span(this, name, level, fields?.toFields(), startTimeMs, parentSpanId)

    override fun log(httpRequestInfo: HttpRequestInfo) = add(BufferedCall.HttpRequest(httpRequestInfo, getTimeStampInMs()))

    override fun log(httpResponseInfo: HttpResponseInfo) = add(BufferedCall.HttpResponse(httpResponseInfo, getTimeStampInMs()))

    override fun setSleepMode(sleepMode: SleepMode) = add(BufferedCall.SetSleepMode(sleepMode))

    override fun logInternal(
        type: LogType,
        level: LogLevel,
        arrayFields: ArrayFields,
        matchingArrayFields: ArrayFields,
        attributesOverrides: LogAttributesOverrides?,
        blocking: Boolean,
        message: () -> String,
    ) = add(
        BufferedCall.LogInternal(
            type = type,
            level = level,
            arrayFields = arrayFields,
            matchingArrayFields = matchingArrayFields,
            // In order to keep the original emission log time when replayed later
            attributesOverrides = attributesOverrides ?: LogAttributesOverrides.OccurredAt(getTimeStampInMs()),
            blocking = blocking,
            message = message(),
        ),
    )

    override fun logInternal(
        type: LogType,
        level: LogLevel,
        arrayFields: ArrayFields,
        throwable: Throwable?,
        blocking: Boolean,
        message: () -> String,
    ) = logInternal(
        type = type,
        level = level,
        arrayFields = combineFields(arrayFields, throwableFields(throwable)),
        blocking = blocking,
        message = message,
    )

    override fun updateOotbField(
        key: String,
        value: String,
    ) = add(BufferedCall.UpdateOotbField(key, value))

    override fun logInternalError(
        throwable: Throwable?,
        blocking: Boolean,
        message: () -> String,
    ) = add(BufferedCall.LogInternalError(throwable, blocking, message()))

    override fun handleInternalError(
        detail: String,
        throwable: Throwable?,
    ) = add(BufferedCall.HandleInternalError(detail, throwable))

    override fun flush(blocking: Boolean) = add(BufferedCall.Flush(blocking))

    override fun logResourceUtilization(
        arrayFields: ArrayFields,
        duration: Duration,
    ) = add(BufferedCall.LogResourceUtilization(arrayFields, duration))

    override fun logSessionReplayScreenshot(
        fields: Array<Field>,
        duration: Duration,
    ) = add(BufferedCall.LogSessionReplayScreenshot(fields, duration))

    override fun logSessionReplayScreen(
        fields: Array<Field>,
        duration: Duration,
    ) = add(BufferedCall.LogSessionReplayScreen(fields, duration))

    override fun notifyMemoryPressureLevel(level: MemoryPressureLevel) = add(BufferedCall.NotifyMemoryPressureLevel(level))

    override fun getPreviousRunMemoryPressureLevel(): MemoryPressureLevel = MemoryPressureLevel.Unknown

    /**
     * Dispatches all buffered calls into the ready native [logger] instance in the order they occurred
     */
    fun flushToNative(logger: IInternalLogger) {
        drainTarget.set(logger)
        drainTo(logger)
    }

    /**
     * Discards any buffered calls without dispatching them, and marks this instance permanently
     * dead so any later call is dropped immediately instead of being buffered. Called after a
     * failed SDK start.
     */
    fun cleanUp() {
        failed.set(true)
        bufferedCalls.clear()
        bufferedBytes.set(0)
    }

    private fun getTimeStampInMs(): Long = dateProvider?.invoke()?.time ?: System.currentTimeMillis()

    private fun add(call: BufferedCall) {
        if (failed.get()) return

        drainTarget.get()?.let {
            call.dispatch(it)
            return
        }

        if (!tryReserveBytes(call.sizeBytes)) {
            droppedCallCount.incrementAndGet()
            return
        }

        bufferedCalls.add(call)

        drainTarget.get()?.let(::drainTo)
    }

    private fun drainTo(logger: IInternalLogger) {
        generateSequence { bufferedCalls.poll() }.forEach { call ->
            bufferedBytes.addAndGet(-call.sizeBytes)
            call.dispatch(logger)
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

    private fun tryReserveBytes(bytes: Int): Boolean {
        if (bufferedBytes.addAndGet(bytes) <= MAX_BUFFER_BYTES) return true

        bufferedBytes.addAndGet(-bytes)
        return false
    }

    private sealed interface BufferedCall {
        val sizeBytes: Int

        fun dispatch(logger: IInternalLogger)

        data class StartNewSession(
            val sessionId: String?,
        ) : BufferedCall {
            override val sizeBytes = sessionId?.let { sized(it) } ?: OVERHEAD_BYTES

            override fun dispatch(logger: IInternalLogger) = logger.startNewSession(sessionId)
        }

        data class AddField(
            val key: String,
            val value: String,
        ) : BufferedCall {
            override val sizeBytes = sized(key, value)

            override fun dispatch(logger: IInternalLogger) = logger.addField(key, value)
        }

        data class RemoveField(
            val key: String,
        ) : BufferedCall {
            override val sizeBytes = sized(key)

            override fun dispatch(logger: IInternalLogger) = logger.removeField(key)
        }

        data class StringFeatureFlagExposure(
            val name: String,
            val variant: String,
        ) : BufferedCall {
            override val sizeBytes = sized(name, variant)

            override fun dispatch(logger: IInternalLogger) = logger.setFeatureFlagExposure(name, variant)
        }

        data class BooleanFeatureFlagExposure(
            val name: String,
            val variant: Boolean,
        ) : BufferedCall {
            override val sizeBytes = sized(name)

            override fun dispatch(logger: IInternalLogger) = logger.setFeatureFlagExposure(name, variant)
        }

        data class SetEntityId(
            val entityId: String,
        ) : BufferedCall {
            override val sizeBytes = sized(entityId)

            override fun dispatch(logger: IInternalLogger) = logger.setEntityId(entityId)
        }

        data object ClearEntityId : BufferedCall {
            override val sizeBytes = OVERHEAD_BYTES

            override fun dispatch(logger: IInternalLogger) = logger.clearEntityId()
        }

        data class Log(
            val level: LogLevel,
            val fields: ArrayFields,
            val occurredAtMs: Long,
            val message: String,
        ) : BufferedCall {
            override val sizeBytes = sized(message) + fields.sizeBytes()

            override fun dispatch(logger: IInternalLogger) {
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

            override fun dispatch(logger: IInternalLogger) = logger.logAppLaunchTTI(duration)
        }

        data class ScreenView(
            val screenName: String,
        ) : BufferedCall {
            override val sizeBytes = sized(screenName)

            override fun dispatch(logger: IInternalLogger) = logger.logScreenView(screenName)
        }

        data class LogInternal(
            val type: LogType,
            val level: LogLevel,
            val arrayFields: ArrayFields,
            val matchingArrayFields: ArrayFields,
            val attributesOverrides: LogAttributesOverrides?,
            val blocking: Boolean,
            val message: String,
        ) : BufferedCall {
            override val sizeBytes = sized(message) + arrayFields.sizeBytes() + matchingArrayFields.sizeBytes()

            override fun dispatch(logger: IInternalLogger) {
                logger.logInternal(
                    type = type,
                    level = level,
                    arrayFields = arrayFields,
                    matchingArrayFields = matchingArrayFields,
                    attributesOverrides = attributesOverrides,
                    blocking = blocking,
                ) { message }
            }
        }

        data class UpdateOotbField(
            val key: String,
            val value: String,
        ) : BufferedCall {
            override val sizeBytes = sized(key, value)

            override fun dispatch(logger: IInternalLogger) = logger.updateOotbField(key, value)
        }

        data class LogInternalError(
            val throwable: Throwable?,
            val blocking: Boolean,
            val message: String,
        ) : BufferedCall {
            override val sizeBytes = sized(message)

            override fun dispatch(logger: IInternalLogger) = logger.logInternalError(throwable, blocking) { message }
        }

        data class HandleInternalError(
            val detail: String,
            val throwable: Throwable?,
        ) : BufferedCall {
            override val sizeBytes = sized(detail)

            override fun dispatch(logger: IInternalLogger) = logger.handleInternalError(detail, throwable)
        }

        data class Flush(
            val blocking: Boolean,
        ) : BufferedCall {
            override val sizeBytes = OVERHEAD_BYTES

            override fun dispatch(logger: IInternalLogger) = logger.flush(blocking)
        }

        data class LogResourceUtilization(
            val arrayFields: ArrayFields,
            val duration: Duration,
        ) : BufferedCall {
            override val sizeBytes = arrayFields.sizeBytes() + OVERHEAD_BYTES

            override fun dispatch(logger: IInternalLogger) = logger.logResourceUtilization(arrayFields, duration)
        }

        data class LogSessionReplayScreenshot(
            val fields: Array<Field>,
            val duration: Duration,
        ) : BufferedCall {
            override val sizeBytes = fields.sizeBytes() + OVERHEAD_BYTES

            override fun dispatch(logger: IInternalLogger) = logger.logSessionReplayScreenshot(fields, duration)
        }

        data class LogSessionReplayScreen(
            val fields: Array<Field>,
            val duration: Duration,
        ) : BufferedCall {
            override val sizeBytes = fields.sizeBytes() + OVERHEAD_BYTES

            override fun dispatch(logger: IInternalLogger) = logger.logSessionReplayScreen(fields, duration)
        }

        data class NotifyMemoryPressureLevel(
            val level: MemoryPressureLevel,
        ) : BufferedCall {
            override val sizeBytes = OVERHEAD_BYTES

            override fun dispatch(logger: IInternalLogger) = logger.notifyMemoryPressureLevel(level)
        }

        data class HttpRequest(
            val request: HttpRequestInfo,
            val occurredAtMs: Long,
        ) : BufferedCall {
            override val sizeBytes = sized(request.toString())

            override fun dispatch(logger: IInternalLogger) {
                logger.logInternal(
                    type = LogType.SPAN,
                    level = LogLevel.DEBUG,
                    arrayFields = request.arrayFields,
                    matchingArrayFields = request.matchingArrayFields,
                    attributesOverrides = LogAttributesOverrides.OccurredAt(occurredAtMs),
                ) { request.name }
            }
        }

        data class HttpResponse(
            val response: HttpResponseInfo,
            val occurredAtMs: Long,
        ) : BufferedCall {
            override val sizeBytes = sized(response.toString())

            override fun dispatch(logger: IInternalLogger) {
                logger.logInternal(
                    type = LogType.SPAN,
                    level = LogLevel.DEBUG,
                    arrayFields = response.arrayFields,
                    matchingArrayFields = response.matchingArrayFields,
                    attributesOverrides = LogAttributesOverrides.OccurredAt(occurredAtMs),
                ) { response.name }
            }
        }

        data class SetSleepMode(
            val sleepMode: SleepMode,
        ) : BufferedCall {
            override val sizeBytes = OVERHEAD_BYTES

            override fun dispatch(logger: IInternalLogger) = logger.setSleepMode(sleepMode)
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

        private fun Array<Field>.sizeBytes(): Int = sumOf { stringBytes(it.key) + stringBytes(it.value.toString()) }

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
