// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.SessionReplayController
import io.bitdrift.capture.replay.internal.mappers.ViewMapper

internal class ReplayParser(
    sessionReplayConfiguration: SessionReplayConfiguration,
    private val errorHandler: ErrorHandler,
    private val windowManager: IWindowManager,
    private val displayManager: DisplayManagers,
    private val viewMapper: ViewMapper = ViewMapper(sessionReplayConfiguration),
) {
    /**
     * Parses a ScannableView tree hierarchy into a list of ReplayRect
     */
    fun parse(
        replayCaptureMetrics: ReplayCaptureMetrics,
        skipReplayComposeViews: Boolean,
    ): FilteredCapture {
        val result = mutableListOf<ReplayRect>()

        // 1. Add screen size as the first element
        val bounds = displayManager.computeDisplayRect()
        SessionReplayController.L.d("Display Screen size $bounds")
        result.add(bounds)

        val rootViews = windowManager.getAllRootViews()
        val imeBoundsList = mutableListOf<ReplayRect>()

        // Use a stack to perform a DFS traversal of the tree and avoid recursion
        val stack: ArrayDeque<ScannableView> =
            ArrayDeque(
                // reverse the list to get the required z-order (top to bottom)
                rootViews.reversed().map { rootView ->
                    SessionReplayController.L.d("Root view found and added to list: ${rootView.javaClass.simpleName}")

                    // 2. Collect Keyboard overlay info while we have the root views
                    ViewCompat.getRootWindowInsets(rootView)?.let { windowInset ->
                        val imeType = WindowInsetsCompat.Type.ime()
                        if (windowInset.isVisible(imeType)) {
                            val insets = windowInset.getInsets(imeType)
                            val imeBounds =
                                ReplayRect(
                                    type = ReplayType.Keyboard,
                                    x = rootView.left,
                                    y = rootView.bottom - insets.bottom,
                                    width = rootView.width,
                                    height = insets.bottom,
                                )
                            SessionReplayController.L.d("Keyboard IME size $imeBounds")
                            imeBoundsList.add(imeBounds)
                        }
                    }

                    ScannableView.AndroidView(rootView, skipReplayComposeViews)
                },
            )

        while (stack.isNotEmpty()) {
            val currentNode = stack.removeLast()
            try {
                viewMapper.updateMetrics(currentNode, replayCaptureMetrics)
                if (!viewMapper.viewIsVisible(currentNode)) {
                    SessionReplayController.L.v("Ignoring not visible view: ${currentNode.displayName}")
                    continue
                }

                // 3. Map view and filter out Ignored types (addressing TODO in ReplayFilter)
                val mappedViews = viewMapper.mapView(currentNode)
                for (mappedView in mappedViews) {
                    if (mappedView.type != ReplayType.Ignore) {
                        result.add(mappedView)
                    }
                }
            } catch (e: Throwable) {
                val errorMsg = "Error parsing view, Skipping $currentNode and children"
                SessionReplayController.L.e(e, errorMsg)
                replayCaptureMetrics.exceptionCausingViewCount += 1
                errorHandler.handleError(errorMsg, e)
            }
            // Convert the sequence of children to a list to process in reverse order
            val childrenList = currentNode.children.toList()
            // Add the children to the stack in reverse order so that the leftmost child is processed first
            for (i in childrenList.size - 1 downTo 0) {
                stack.addLast(childrenList[i])
            }
        }

        // 4. Add Keyboard overlays at the end (top-most in Z-order)
        result.addAll(imeBoundsList)

        return result
    }
}
