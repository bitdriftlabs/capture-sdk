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

/**
 * Decides whether a view's background occludes what is behind it.
 *
 * [ReplayType.BackgroundImage] is intentionally never returned: it denotes an image used as a
 * backdrop, not an opaque fill.
 */
internal object BackgroundOpacity {
    /** Returns the type this view's background paints as, or null if it has no background. */
    @Suppress("DEPRECATION")
    fun paintedType(view: View): ReplayType? {
        val background = view.background ?: return null
        // TRANSLUCENT, TRANSPARENT and unreportable opacities all let content through.
        return if (background.opacity == PixelFormat.OPAQUE) ReplayType.View else ReplayType.TransparentView
    }
}
