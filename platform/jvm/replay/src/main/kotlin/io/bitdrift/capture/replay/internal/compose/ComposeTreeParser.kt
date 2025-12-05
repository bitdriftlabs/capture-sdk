// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.replay.internal.compose

import android.view.View
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.toSize
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.SessionReplayController
import io.bitdrift.capture.replay.compose.CaptureModifier
import io.bitdrift.capture.replay.internal.ReplayRect
import io.bitdrift.capture.replay.internal.ScannableView
import java.lang.reflect.InvocationTargetException

internal object ComposeTreeParser {
    @OptIn(InternalComposeUiApi::class)
    internal fun parse(androidComposeView: View): ScannableView {
        if (androidComposeView !is AndroidComposeView) {
            SessionReplayController.L.e(
                null,
                "View passed to ComposeTreeParser.parse() is not an AndroidComposeView. view=${androidComposeView::class.qualifiedName}",
            )
            return ScannableView.IgnoredComposeView
        }

        val semanticsOwner = androidComposeView.semanticsOwner

        // Calculate the window's offset on screen to properly translate Compose coordinates
        val windowOffset = IntArray(2)
        androidComposeView.rootView.getLocationOnScreen(windowOffset)

        val layoutNodeMap = buildSemanticsIdToLayoutNodeMap(androidComposeView.root)

        val rootNode = semanticsOwner.unmergedRootSemanticsNode
        SessionReplayController.L.d(
            "Found Compose SemanticsNode root. Parsing Compose tree. Window offset: (${windowOffset[0]}, ${windowOffset[1]})",
        )
        return rootNode.toScannableView(windowOffset[0], windowOffset[1], layoutNodeMap)
    }

    private fun buildSemanticsIdToLayoutNodeMap(rootNode: LayoutNode): Map<Int, LayoutNode> {
        val map = mutableMapOf<Int, LayoutNode>()
        populateSemanticsIdToLayoutNodeMap(rootNode, map)
        return map
    }

    private fun populateSemanticsIdToLayoutNodeMap(node: LayoutNode, map: MutableMap<Int, LayoutNode>) {
        if (node.semanticsId != 0) {
            map[node.semanticsId] = node
        }
        node.zSortedChildren.forEach {
            populateSemanticsIdToLayoutNodeMap(it, map)
        }
    }

    @OptIn(InternalComposeUiApi::class)
    private fun SemanticsNode.toScannableView(
        windowOffsetX: Int,
        windowOffsetY: Int,
        layoutNodeMap: Map<Int, LayoutNode>,
    ): ScannableView {
        val layoutNode = layoutNodeMap[this.id] ?: return ScannableView.IgnoredComposeView
        val notAttachedOrPlaced = !layoutNode.isPlaced || !layoutNode.isAttached
        val captureIgnoreSubTree = this.unmergedConfig.getOrNull(CaptureModifier.CaptureIgnore)
        val isVisible = !this.isTransparent && !this.unmergedConfig.contains(SemanticsProperties.InvisibleToUser)
        val type =
            if (notAttachedOrPlaced) {
                return ScannableView.IgnoredComposeView
            } else if (captureIgnoreSubTree != null) {
                if (captureIgnoreSubTree) {
                    // short-circuit the entire sub-tree
                    return ScannableView.IgnoredComposeView
                } else {
                    // just ignore this one element
                    ReplayType.Ignore
                }
            } else if (!isVisible) {
                ReplayType.TransparentView
            } else {
                this.unmergedConfig.toReplayType()
            }

        // Handle hybrid interop AndroidViews inside Compose elements
        val interopAndroidView = layoutNode.getInteropView()
        if (type == ReplayType.View && interopAndroidView != null) {
            return ScannableView.AndroidView(
                view = interopAndroidView,
                skipReplayComposeViews = false,
            )
        }

        val nodeBounds = this.unclippedGlobalBounds

        return ScannableView.ComposeView(
            replayRect =
                ReplayRect(
                    type = type,
                    // Add window offset to translate from window-relative to screen coordinates
                    x = nodeBounds.left.toInt() + windowOffsetX,
                    y = nodeBounds.top.toInt() + windowOffsetY,
                    width = nodeBounds.width.toInt(),
                    height = nodeBounds.height.toInt(),
                ),
            // The display name is not really used for anything
            displayName = "ComposeView",
            // Pass window offset to all children
            children = this.children.asSequence().map { it.toScannableView(windowOffsetX, windowOffsetY, layoutNodeMap) },
        )
    }

    private fun SemanticsConfiguration.toReplayType(): ReplayType {
        val role = this.getOrNull(SemanticsProperties.Role)
        return if (this.contains(SemanticsProperties.Text)) {
            ReplayType.Label
        } else if (this.contains(SemanticsActions.SetText)) {
            ReplayType.TextInput
        } else if (role == Role.Button) {
            ReplayType.Button
        } else if (role == Role.Image) {
            ReplayType.Image
        } else if (role == Role.Checkbox) {
            if (this.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On) {
                ReplayType.SwitchOn
            } else {
                ReplayType.SwitchOff
            }
        } else {
            ReplayType.View
        }
    }

    private val SemanticsNode.unclippedGlobalBounds: Rect
        get() {
            return Rect(positionInWindow, size.toSize())
        }
}
