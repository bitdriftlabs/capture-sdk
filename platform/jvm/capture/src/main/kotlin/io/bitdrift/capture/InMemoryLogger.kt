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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration

/**
 *
 * Given that Logger.start() runs on dedicated thread, will be caching any calls to Logger.log while
 * start is in process of being initialized.
 *
 * When Logger.start() is completed will flush the in memory calls to the native layer
 */
internal class InMemoryLogger : ILogger {

    private val bufferedLogsCalls = CopyOnWriteArrayList<(ILogger) -> Unit>()

    override val sessionId: String = DEFAULT_NOT_SETUP_MESSAGE

    override val sessionUrl: String = DEFAULT_NOT_SETUP_MESSAGE

    override val deviceId: String = DEFAULT_NOT_SETUP_MESSAGE

    override fun startNewSession() {
        bufferedLogsCalls.add { it.startNewSession() }
    }

    override fun createTemporaryDeviceCode(completion: (CaptureResult<String>) -> Unit) {
        bufferedLogsCalls.add { it.createTemporaryDeviceCode(completion) }
    }

    override fun addField(key: String, value: String) {
        bufferedLogsCalls.add { it.addField(key, value) }
    }

    override fun removeField(key: String) {
        bufferedLogsCalls.add { it.removeField(key) }
    }

    override fun log(
        level: LogLevel,
        fields: Map<String, String>?,
        throwable: Throwable?,
        message: () -> String
    ) {
        bufferedLogsCalls.add { it.log(level, fields, throwable, message) }
    }

    override fun logAppLaunchTTI(duration: Duration) {
        bufferedLogsCalls.add { it.logAppLaunchTTI(duration) }
    }

    override fun logScreenView(screenName: String) {
        bufferedLogsCalls.add { it.logScreenView(screenName) }
    }

    override fun startSpan(
        name: String,
        level: LogLevel,
        fields: Map<String, String>?,
        startTimeMs: Long?,
        parentSpanId: UUID?
    ): Span {
        val span = Span(null, name, level, fields, startTimeMs, parentSpanId)
        bufferedLogsCalls.add { it.startSpan(name, level, fields, startTimeMs, parentSpanId) }
        return span
    }

    override fun log(httpRequestInfo: HttpRequestInfo) {
        bufferedLogsCalls.add { it.log(httpRequestInfo) }
    }

    override fun log(httpResponseInfo: HttpResponseInfo) {
        bufferedLogsCalls.add { it.log(httpResponseInfo) }
    }

    /** Flush all buffered logs into the real `LoggerImpl` **/
    fun flushToNative(loggerImpl: LoggerImpl) {
        bufferedLogsCalls.forEach { it(loggerImpl) }
        bufferedLogsCalls.clear()
    }

    /** Clear stored log actions **/
    fun clear(){
        bufferedLogsCalls.clear()
    }

    private companion object{
        private const val DEFAULT_NOT_SETUP_MESSAGE = "SDK starting"
    }
}