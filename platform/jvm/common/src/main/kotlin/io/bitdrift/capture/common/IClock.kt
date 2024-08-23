// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.common

/**
 * A clock that returns the current time in milliseconds since the system was booted.
 * Interface is provided to allow for custom clocks to be used in place of the default.
 */
interface IClock {

    /**
     * The time since the system was booted.
     * This clock is guaranteed to be monotonic, and continues to tick even when the CPU is in power saving modes,
     * so is the recommended basis for general purpose interval timing.
     * @return the time since the system was booted, including deep sleep.
     */
    fun elapsedRealtime(): Long
}
