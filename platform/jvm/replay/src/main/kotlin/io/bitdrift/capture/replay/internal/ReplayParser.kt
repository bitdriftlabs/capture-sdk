// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.SessionReplayController
import io.bitdrift.capture.replay.internal.mappers.ViewMapper

internal typealias Capture = List<List<ReplayRect>>

internal class ReplayParser(
    sessionReplayConfiguration: SessionReplayConfiguration,
    private val errorHandler: ErrorHandler,
    private val windowManager: IWindowManager,
    private val viewMapper: ViewMapper = ViewMapper(sessionReplayConfiguration),
) {
    /**
     * Parses a ScannableView tree hierarchy into a list of ReplayRect
     */
    fun parse(
        replayCaptureMetrics: ReplayCaptureMetrics,
        skipReplayComposeViews: Boolean,
    ): Capture {
        val result = mutableListOf<List<ReplayRect>>()

        // Use a stack to perform a DFS traversal of the tree and avoid recursion
        val stack: ArrayDeque<ScannableView> =
            ArrayDeque(
                // reverse the list to get the required z-order (top to bottom)
                windowManager.getAllRootViews().reversed().map {
                    SessionReplayController.L.d("Root view found and added to list: ${it.javaClass.simpleName}")
                    ScannableView.AndroidView(it, skipReplayComposeViews)
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
                result.add(viewMapper.mapView(currentNode))
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

        return result
    }
}
