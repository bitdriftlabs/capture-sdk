// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.span

import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.providers.toFields
import java.util.UUID

internal object SpanField {
    object Key {
        const val ID = "_span_id"
        const val NAME = "_span_name"
        const val TYPE = "_span_type"
        const val DURATION = "_duration_ms"
        const val RESULT = "_result"
    }

    object Value {
        const val TYPE_START = "start"
        const val TYPE_END = "end"
    }
}

/**
 * Represents a single operation that is started and can be ended. The SDK emits two logs
 * for each span: one when the span is started and another when the `Span.end()` method is called.
 */
class Span internal constructor(
    private var logger: LoggerImpl?,
    private val name: String,
    private val level: LogLevel,
    fields: Map<String, String>? = null,
    private val clock: IClock = DefaultClock.getInstance(),
) {
    private val id: UUID = UUID.randomUUID()

    // Using this API since this clock is guaranteed to be monotonic,
    // and continues to tick even when the CPU is in power saving modes,
    // so is the recommend basis for general purpose interval timing.
    private val startTimeMs: Long = clock.elapsedRealtime()
    private val startFields: Map<String, String> =
        buildMap {
            fields?.let {
                putAll(it)
            }
            put(SpanField.Key.ID, id.toString())
            put(SpanField.Key.NAME, name)
            put(SpanField.Key.TYPE, SpanField.Value.TYPE_START)
        }

    init {
        logger?.log(
            LogType.SPAN,
            level,
            startFields.toFields(),
        ) { "" }
    }

    /**
     * Signals that the operation described by this span has now ended. It automatically records
     * its duration up to this point.
     *
     * An operation can be ended only once, consecutive calls to this method have no effect.
     *
     * @param result the result of the operation.
     * @param fields additional fields to include in the log.
     */
    fun end(
        result: SpanResult,
        fields: Map<String, String>? = null,
    ) {
        logger?.apply {
            val endFields =
                buildMap {
                    putAll(startFields)
                    fields?.let {
                        putAll(it)
                    }
                    put(SpanField.Key.TYPE, SpanField.Value.TYPE_END)
                    put(SpanField.Key.DURATION, (clock.elapsedRealtime() - startTimeMs).toString())
                    put(SpanField.Key.RESULT, result.toString().lowercase())
                }

            this.log(
                LogType.SPAN,
                level,
                endFields.toFields(),
            ) { "" }
        }
        logger = null
    }
}
