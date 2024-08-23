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
    NORMAL(0),
    REPLAY(1),
    LIFECYCLE(2),
    RESOURCE(3),
    INTERNALSDK(4),
    VIEW(5),
    DEVICE(6),
    UX(7),
    SPAN(8),
}
