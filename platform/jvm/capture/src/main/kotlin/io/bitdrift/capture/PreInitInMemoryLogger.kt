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
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

internal class PreInitInMemoryLogger : ILogger {
    private val bufferedLoggerCalls = ConcurrentLinkedQueue<(IInternalLogger) -> Unit>()
    private val droppedCallCount = AtomicInteger(0)

    // Preserve the non-null ILogger contract while initSdk is still in progress. The outer
    // Capture.Logger getters hide these placeholders by returning null for PreInitInMemoryLogger,
    // So we keep consumers of ILogger expecting a non-null values
    override val sessionId: String = UNKNOWN_VALUE

    override val sessionUrl: String = UNKNOWN_VALUE

    override val deviceId: String = UNKNOWN_VALUE

    override val isTracingActive: Boolean = false

    override fun startNewSession() {
        addLoggerCall { it.startNewSession() }
    }

    override fun createTemporaryDeviceCode(completion: (CaptureResult<String>) -> Unit) {
        addLoggerCall { it.createTemporaryDeviceCode(completion) }
    }

    override fun addField(
        key: String,
        value: String,
    ) {
        addLoggerCall { it.addField(key, value) }
    }

    override fun removeField(key: String) {
        addLoggerCall { it.removeField(key) }
    }

    override fun setFeatureFlagExposure(
        name: String,
        variant: String,
    ) {
        addLoggerCall { it.setFeatureFlagExposure(name, variant) }
    }

    override fun setEntityId(entityId: String) {
        addLoggerCall { it.setEntityId(entityId) }
    }

    override fun clearEntityId() {
        addLoggerCall { it.clearEntityId() }
    }

    override fun setFeatureFlagExposure(
        name: String,
        variant: Boolean,
    ) {
        addLoggerCall { it.setFeatureFlagExposure(name, variant) }
    }

    override fun log(
        level: LogLevel,
        fields: Map<String, String>?,
        throwable: Throwable?,
        message: () -> String,
    ) {
        val occurredAtMs = System.currentTimeMillis()
        addLoggerCall { logger ->
            logger.logInternal(
                type = LogType.NORMAL,
                level = level,
                arrayFields = extractFields(fields, throwable),
                matchingArrayFields = ArrayFields.EMPTY,
                attributesOverrides = LogAttributesOverrides.OccurredAt(occurredAtMs),
                blocking = false,
                message = message,
            )
        }
    }

    override fun log(
        level: LogLevel,
        arrayFields: ArrayFields,
        throwable: Throwable?,
        message: () -> String,
    ) {
        val occurredAtMs = System.currentTimeMillis()
        addLoggerCall { logger ->
            logger.logInternal(
                type = LogType.NORMAL,
                level = level,
                arrayFields = combineFields(arrayFields, throwable.toThrowableFields()),
                matchingArrayFields = ArrayFields.EMPTY,
                attributesOverrides = LogAttributesOverrides.OccurredAt(occurredAtMs),
                blocking = false,
                message = message,
            )
        }
    }

    override fun logAppLaunchTTI(duration: Duration) {
        addLoggerCall { it.logAppLaunchTTI(duration) }
    }

    override fun logScreenView(screenName: String) {
        addLoggerCall { it.logScreenView(screenName) }
    }

    override fun startSpan(
        name: String,
        level: LogLevel,
        fields: Map<String, String>?,
        startTimeMs: Long?,
        parentSpanId: UUID?,
    ): Span {
        addLoggerCall { it.startSpan(name, level, fields, startTimeMs, parentSpanId) }
        return Span(null, name, level, fields?.toFields(), startTimeMs, parentSpanId)
    }

    override fun log(httpRequestInfo: HttpRequestInfo) {
        addLoggerCall { it.log(httpRequestInfo) }
    }

    override fun log(httpResponseInfo: HttpResponseInfo) {
        addLoggerCall { it.log(httpResponseInfo) }
    }

    override fun setSleepMode(sleepMode: SleepMode) {
        addLoggerCall { it.setSleepMode(sleepMode) }
    }

    fun flushToNative(loggerImpl: LoggerImpl) {
        bufferedLoggerCalls.forEach { it(loggerImpl) }
        bufferedLoggerCalls.clear()

        val droppedCalls = droppedCallCount.getAndSet(0)
        if (droppedCalls > 0) {
            loggerImpl.logInternal(
                type = LogType.INTERNALSDK,
                level = LogLevel.WARNING,
                arrayFields = fieldsOf("dropped_call_count" to droppedCalls.toString()),
            ) {
                "Pre-init logger buffer overflowed while SDK was starting; oldest buffered calls were evicted"
            }
        }
    }

    fun clear() {
        bufferedLoggerCalls.clear()
    }

    private fun addLoggerCall(logCall: (IInternalLogger) -> Unit) {
        if (bufferedLoggerCalls.size >= MAX_LOG_CALL_SIZE) {
            bufferedLoggerCalls.poll()
            droppedCallCount.incrementAndGet()
        }
        bufferedLoggerCalls.add(logCall)
    }

    private companion object {
        private const val MAX_LOG_CALL_SIZE = 512
        private const val UNKNOWN_VALUE = "unknown"
    }
}
