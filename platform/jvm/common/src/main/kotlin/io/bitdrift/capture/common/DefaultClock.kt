// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.common

import android.os.SystemClock

/**
 * A clock that returns the current time in milliseconds since the system was booted.
 */
object DefaultClock : IClock {

    /**
     * Returns the singleton instance of the DefaultClock.
     */
    fun getInstance() = this

    /**
     * The time since the system was booted.
     * This clock is guaranteed to be monotonic, and continues to tick even when the CPU is in power saving modes,
     * so is the recommended basis for general purpose interval timing.
     * @return the time since the system was booted, including deep sleep.
     */
    override fun elapsedRealtime(): Long {
        return SystemClock.elapsedRealtime()
    }
}
