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
 * Single source of truth for whether a view's background occludes what is behind it.
 *
 * A view only hides the content below it when it paints an opaque background. The window root
 * (DecorView) of a dialog, bottom sheet or popup is transparent, so classifying it as opaque paints
 * a filled rect across the whole frame and blanks every window beneath it.
 *
 * This deliberately has no [ReplayType.BackgroundImage] case: that type means "an image acting as a
 * backdrop" (see the iOS categorizers for `_UIBarBackground` and backdrop `UIImageView`s), not "an
 * opaque fill", and a background drawable here is usually a plain color.
 */
internal object BackgroundOpacity {
    /**
     * The type this view's background paints as, or `null` when the view has no background and so
     * paints nothing at all. Callers that only want a rect when something is actually drawn -- such
     * as [BackgroundMapper], which contributes a rect *in addition* to any the other mappers
     * produced -- should skip the `null` case.
     */
    @Suppress("DEPRECATION")
    fun paintedType(view: View): ReplayType? {
        val background = view.background ?: return null
        return if (background.opacity == PixelFormat.OPAQUE) {
            ReplayType.View
        } else {
            // PixelFormat.TRANSLUCENT, PixelFormat.TRANSPARENT, or an opacity the drawable cannot
            // report. None of them fully cover what is behind them.
            ReplayType.TransparentView
        }
    }

    /**
     * The type to emit for a view that already has a rect of its own and resolved to the generic
     * [ReplayType.View]. Unlike [paintedType], a missing background is not "no rect" here but "a
     * container that paints nothing", so it is transparent.
     */
    fun containerType(view: View): ReplayType = paintedType(view) ?: ReplayType.TransparentView
}
