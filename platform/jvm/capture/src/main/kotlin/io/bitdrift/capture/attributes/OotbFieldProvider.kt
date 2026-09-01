// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.attributes

import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.providers.Field

/** Supplies OOTB fields at logger creation and forwards later state changes to the logger. */
internal interface OotbFieldProvider {
    /** Returns fields that are available before the logger accepts logs. */
    fun initialOotbFields(): Array<Field> = emptyArray()

    /** Starts forwarding subsequent field updates. */
    fun start(logger: IInternalLogger)
}
