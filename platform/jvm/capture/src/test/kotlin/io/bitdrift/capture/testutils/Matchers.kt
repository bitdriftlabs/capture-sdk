// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.testutils

import com.nhaarman.mockitokotlin2.argThat
import io.bitdrift.capture.providers.FieldValue

fun hasFields(vararg entries: Pair<String, String>): Map<String, FieldValue>? =
    argThat { fields ->
        entries.all { (key, value) ->
            fields?.get(key)?.toString() == value
        }
    }
