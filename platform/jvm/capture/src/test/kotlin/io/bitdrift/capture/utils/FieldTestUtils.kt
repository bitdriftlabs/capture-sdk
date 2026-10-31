// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.utils

import io.bitdrift.capture.providers.ArrayFields

fun ArrayFields.toStringMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    for (i in keys.indices) {
        map[keys[i]] = values[i]
    }
    return map
}
