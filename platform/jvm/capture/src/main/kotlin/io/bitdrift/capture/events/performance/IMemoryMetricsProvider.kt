// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import io.bitdrift.capture.InternalFields

/**
 * Provides Memory related attributes such as Memory class, Total JVM memory, used JVM memory, etc
 */
interface IMemoryMetricsProvider {
    /**
     * Reports current memory attributes
     */
    fun getMemoryAttributes(): InternalFields

    /**
     * Reports memory class type
     */
    fun getMemoryClass(): InternalFields

    /** Reports whether the device is currently experiencing a low memory condition */
    fun isMemoryLow(): Boolean
}
