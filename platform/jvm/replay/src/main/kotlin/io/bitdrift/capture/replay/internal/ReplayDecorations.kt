// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.bitdrift.capture.common.WindowManager
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.SessionReplayController

// Add the screen and keyboard layouts to the replay capture
internal class ReplayDecorations(
    private val displayManager: DisplayManagers,
    private val windowManager: WindowManager,
) {
    fun addDecorations(filteredCapture: FilteredCapture): FilteredCapture {
        // Add screen size as the first element
        val bounds = displayManager.computeDisplayRect()
        SessionReplayController.L.d("Display Screen size $bounds")
        val screen: MutableList<ReplayRect> = mutableListOf(bounds)
        screen.addAll(filteredCapture)

        var imeBounds: ReplayRect
        windowManager.findRootViews().iterator().forEach { rootView ->
            ViewCompat.getRootWindowInsets(rootView)?.let { windowInset ->

                // Add Keyboard overlay
                val imeType = WindowInsetsCompat.Type.ime()
                if (windowInset.isVisible(imeType)) {
                    val insets = windowInset.getInsets(imeType)
                    imeBounds =
                        ReplayRect(
                            type = ReplayType.Keyboard,
                            x = rootView.left,
                            y = rootView.bottom - insets.bottom,
                            width = rootView.width,
                            height = insets.bottom,
                        )
                    SessionReplayController.L.d("Keyboard IME size $imeBounds")
                    screen.add(imeBounds)
                }
            }
        }

        return screen.toList()
    }
}
