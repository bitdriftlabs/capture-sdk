// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * DO NOT USE. This is a copy of the generated enum via bazel+cargo just to make gradle happy.
 * The real file is generated using bitdrift_public.fbs.logging.v1.LogType in github.com/bitdriftlabs/proto
 *
 * @property value the numeric representation of the log type.
 */
enum class LogType(val value: Int) {
    /**
     * Represents a normal log type, from consumer apps code.
     */
    NORMAL(0),
    /**
     * Represents a replay log type, from session replay.
     */
    REPLAY(1),
    /**
     * Represents a lifecycle log type, from automatic instrumentation.
     */
    LIFECYCLE(2),
    /**
     * Represents a resource log type, from automatic instrumentation.
     */
    RESOURCE(3),
    /**
     * Represents an internal SDK log type, from the SDK itself.
     */
    INTERNALSDK(4),
    /**
     * Represents a view lifecycle log type, from automatic instrumentation.
     */
    VIEW(5),
    /**
     * Represents a device log type, from automatic instrumentation.
     */
    DEVICE(6),
    /**
     * Represents a UX interaction log type, from automatic instrumentation.
     */
    UX(7),
    /**
     * Represents a span log type, from automatic instrumentation.
     */
    SPAN(8),
}
