// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal.mappers

import android.graphics.Rect
import android.view.View
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.internal.ReplayRect

internal open class Mapper {
    var viewOriginX = 0
        private set
    var viewOriginY = 0
        private set

    open fun map(view: View): MutableList<ReplayRect> {
        val list = mutableListOf<ReplayRect>()
        val out = IntArray(2)
        view.getLocationOnScreen(out)
        viewOriginX = out[0]
        viewOriginY = out[1]
        return list
    }

    fun addView(
        type: ReplayType = ReplayType.View,
        view: View,
        list: MutableList<ReplayRect>,
    ) {
        list.add(ReplayRect(type, viewOriginX, viewOriginY, view.width, view.height))
    }

    // Offset the given boundaries to the screen coordinates
    fun alignInScreen(bounds: Rect) {
        bounds.offset(viewOriginX, viewOriginY)
    }
}
