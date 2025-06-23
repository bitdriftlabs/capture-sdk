// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal.mappers

import android.graphics.PixelFormat
import android.view.View
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.internal.ReplayRect

@Suppress("DEPRECATION")
internal class BackgroundMapper : Mapper() {
    override fun map(view: View): MutableList<ReplayRect> {
        val list = super.map(view)

        view.background?.let { drawable ->
            val type =
                when (drawable.opacity) {
                    PixelFormat.OPAQUE -> {
                        ReplayType.BackgroundImage
                    }
                    PixelFormat.TRANSLUCENT -> {
                        ReplayType.TransparentView
                    }
                    else -> {
                        // is Transparent or unknown
                        ReplayType.Ignore
                    }
                }
            list.add(ReplayRect(type, viewOriginX, viewOriginY, view.width, view.height))
        }
        return list
    }
}
