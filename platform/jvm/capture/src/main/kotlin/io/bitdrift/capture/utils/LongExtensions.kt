// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.utils

/**
 * Narrows to an Int, saturating at its bounds instead of wrapping as [Long.toInt] does. For a
 * 64-bit value (such as a command argument, whose integer types are all 64-bit) handed to an API
 * that takes an Int and clamps it to its own, smaller range.
 */
internal fun Long.toIntSaturated(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
