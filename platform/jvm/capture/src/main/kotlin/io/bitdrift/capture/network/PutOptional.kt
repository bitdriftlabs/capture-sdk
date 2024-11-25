// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

import io.bitdrift.capture.providers.FieldValue

internal fun <T> MutableMap<String, FieldValue>.putOptional(
    key: String,
    value: T?,
    extractString: ((T) -> String) = { t -> t.toString() },
): MutableMap<String, FieldValue> {
    if (value != null) {
        this[key] = FieldValue.StringField(extractString(value))
    }
    return this
}
