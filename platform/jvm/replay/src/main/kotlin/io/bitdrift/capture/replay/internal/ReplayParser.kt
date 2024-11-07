// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.replay.ReplayManager
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.internal.mappers.ViewMapper

internal typealias Capture = List<List<ReplayRect>>

internal class ReplayParser(
    sessionReplayConfiguration: SessionReplayConfiguration,
    private val errorHandler: ErrorHandler,
    private val viewMapper: ViewMapper = ViewMapper(sessionReplayConfiguration),
) {

    private val windowManager = WindowManager(errorHandler)

    /**
     * Parses a ScannableView tree hierarchy into a list of ReplayRect
     */
    fun parse(encodedScreenMetrics: EncodedScreenMetrics, skipReplayComposeViews: Boolean): Capture {
        val result = mutableListOf<List<ReplayRect>>()

        // Use a stack to perform a DFS traversal of the tree and avoid recursion
        val stack: ArrayDeque<ScannableView> = ArrayDeque(
            windowManager.findRootViews().map {
                ReplayManager.L.v("Root view found and added to list: ${it.javaClass.simpleName}")
                ScannableView.AndroidView(it, skipReplayComposeViews)
            },
        )
        while (stack.isNotEmpty()) {
            val currentNode = stack.removeLast()
            try {
                viewMapper.updateMetrics(currentNode, encodedScreenMetrics)
                if (!viewMapper.viewIsVisible(currentNode)) {
                    ReplayManager.L.v("Ignoring not visible view: ${currentNode.displayName}")
                    continue
                }
                result.add(viewMapper.mapView(currentNode))
            } catch (e: Throwable) {
                val errorMsg = "Error parsing view, Skipping $currentNode and children"
                ReplayManager.L.e(e, errorMsg)
                encodedScreenMetrics.exceptionCausingViewCount += 1
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
