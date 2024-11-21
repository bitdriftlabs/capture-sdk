// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

object CaptureModifier {

    val CaptureIgnore = SemanticsPropertyKey<Boolean>(
        name = "CaptureIgnoreModifier",
        mergePolicy = { parentValue, _ ->
            parentValue
        },
    )

    @JvmStatic
    fun Modifier.captureIgnore(ignoreSubTree: Boolean = false): Modifier {
        return semantics(
            properties = {
                this[CaptureIgnore] = ignoreSubTree
            },
        )
    }
}
