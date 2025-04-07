// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.span

import io.bitdrift.capture.LogAttributesOverrides
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
        const val PARENT = "_span_parent_id"
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
    /**
     * The human readable name of the span. This doesn't need to be unique.
     */
    val name: String,
    private val level: LogLevel,
    fields: Map<String, String>? = null,
    private val customStartTimeMs: Long? = null,
    /**
     * An optional ID of the parent span, used to build span hierarchies. A span without a
     * parentSpanID is considered a root span.
     */
    val parentSpanId: UUID? = null,
    private val clock: IClock = DefaultClock.getInstance(),
) {
    /**
     * This is the autogenerated unique identifier of the span
     */
    val id: UUID = UUID.randomUUID()

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
            parentSpanId?.let {
                put(SpanField.Key.PARENT, it.toString())
            }
        }

    init {
        logger?.log(
            LogType.SPAN,
            level,
            startFields.toFields(),
            attributesOverrides = customStartTimeMs?.let { LogAttributesOverrides.OccurredAt(it) },
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
     * @param endTimeMs an optional custom end time in milliseconds since the Unix epoch. This can be
     *                  used to override the default end time of the span. If provided, it needs
     *                  to be used in combination with `startTimeMs` at span creation. Providing one and
     *                  not the other is considered an error and in that scenario, the default clock will
     *                  be used instead.
     */
    fun end(
        result: SpanResult,
        fields: Map<String, String>? = null,
        endTimeMs: Long? = null,
    ) {
        logger?.apply {
            val durationMs: Long =
                if (endTimeMs != null && customStartTimeMs != null) {
                    endTimeMs - customStartTimeMs
                } else {
                    clock.elapsedRealtime() - startTimeMs
                }

            val endFields =
                buildMap {
                    putAll(startFields)
                    fields?.let {
                        putAll(it)
                    }
                    put(SpanField.Key.TYPE, SpanField.Value.TYPE_END)
                    put(SpanField.Key.DURATION, durationMs.toString())
                    put(SpanField.Key.RESULT, result.toString().lowercase())
                }

            this.log(
                LogType.SPAN,
                level,
                endFields.toFields(),
                attributesOverrides = endTimeMs?.let { LogAttributesOverrides.OccurredAt(it) },
            ) { "" }
        }
        logger = null
    }
}
