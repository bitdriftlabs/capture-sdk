// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture

import androidx.annotation.OpenForTesting
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration

/**
 *
 * Given that Logger.start() runs on dedicated thread, will be caching any calls to Logger.log while
 * start is in process of being initialized.
 *
 * When Logger.start() is completed will flush the in memory calls to the native layer
 */
internal class PreInitInMemoryLogger :
    ILogger,
    IPreInitLogFlusher {
    private val _bufferedLoggerCalls = ConcurrentLinkedQueue<(ILogger) -> Unit>()

    @get:OpenForTesting
    val bufferedLoggerCalls: List<(ILogger) -> Unit>
        get() = _bufferedLoggerCalls.toList()

    override val sessionId: String = DEFAULT_NOT_SETUP_MESSAGE

    override val sessionUrl: String = DEFAULT_NOT_SETUP_MESSAGE

    override val deviceId: String = DEFAULT_NOT_SETUP_MESSAGE

    /** Flush all in memory Logger calls into the Native `LoggerImpl` **/
    override fun flushToNative(nativeLogger: ILogger) {
        _bufferedLoggerCalls.forEach { it(nativeLogger) }
        _bufferedLoggerCalls.clear()
    }

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

    override fun log(
        level: LogLevel,
        fields: Map<String, String>?,
        throwable: Throwable?,
        message: () -> String,
    ) {
        addLoggerCall { it.log(level, fields, throwable, message) }
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
        val span = Span(null, name, level, fields, startTimeMs, parentSpanId)
        addLoggerCall { it.startSpan(name, level, fields, startTimeMs, parentSpanId) }
        return span
    }

    override fun log(httpRequestInfo: HttpRequestInfo) {
        addLoggerCall { it.log(httpRequestInfo) }
    }

    override fun log(httpResponseInfo: HttpResponseInfo) {
        addLoggerCall { it.log(httpResponseInfo) }
    }

    /** Clear stored Logger calls **/
    fun clear() {
        _bufferedLoggerCalls.clear()
    }

    private fun addLoggerCall(logCall: (ILogger) -> Unit) {
        if (_bufferedLoggerCalls.size >= MAX_LOG_CALL_SIZE) {
            _bufferedLoggerCalls.poll()
        }
        _bufferedLoggerCalls.add(logCall)
    }

    private companion object {
        private const val DEFAULT_NOT_SETUP_MESSAGE = "SDK starting"
        private const val MAX_LOG_CALL_SIZE = 1024
    }
}
