// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal.mappers

import android.view.View
import io.bitdrift.capture.replay.internal.ReplayRect

internal class BackgroundMapper : Mapper() {
    override fun map(view: View): MutableList<ReplayRect> {
        val list = super.map(view)

        // Runs alongside ButtonMapper and TextMapper, so only contribute a rect when the
        // background actually paints.
        BackgroundOpacity.paintedType(view)?.let { type ->
            list.add(ReplayRect(type, viewOriginX, viewOriginY, view.width, view.height))
        }
        return list
    }
}
