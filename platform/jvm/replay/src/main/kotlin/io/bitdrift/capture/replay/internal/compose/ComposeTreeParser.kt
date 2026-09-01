// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.replay.internal.compose

import android.view.View
import androidx.collection.MutableIntObjectMap
import androidx.compose.ui.ExperimentalComposeUiApi
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
import io.bitdrift.capture.replay.internal.mappers.BackgroundOpacity

private const val SHAPE_KEY = "Shape"

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

        // compute a map of semanticsId to LayoutNode for quick lookup during the recursive parsing of SemanticsNode tree
        val layoutNodeMap = buildSemanticsIdToLayoutNodeMap(androidComposeView.root)

        val rootNode = semanticsOwner.unmergedRootSemanticsNode
        SessionReplayController.L.d(
            "Found Compose SemanticsNode root. Parsing Compose tree. Window offset: (${windowOffset[0]}, ${windowOffset[1]})",
        )
        return rootNode.toScannableView(
            ComposeWindow(
                offsetX = windowOffset[0],
                offsetY = windowOffset[1],
                layoutNodes = layoutNodeMap,
                isOverlay = androidComposeView.rootView.isOverlayWindowRoot(),
            ),
        )
    }

    private fun buildSemanticsIdToLayoutNodeMap(rootNode: LayoutNode): MutableIntObjectMap<LayoutNode> {
        val map = MutableIntObjectMap<LayoutNode>()
        populateSemanticsIdToLayoutNodeMap(rootNode, map)
        return map
    }

    /**
     * Recursively traverses the [LayoutNode] tree starting from the given [node],
     * and populates the provided [map] with a mapping from the semantics ID of each node
     * to the node itself. This creates a quick lookup table that can be used to find a
     * [LayoutNode] by its [SemanticsNode] ID, avoiding repeated tree traversals.
     *
     * @param node The starting [LayoutNode] for the traversal.
     * @param map The mutable map to populate with `semanticsId` to `LayoutNode` pairs.
     */
    private fun populateSemanticsIdToLayoutNodeMap(
        node: LayoutNode,
        map: MutableIntObjectMap<LayoutNode>,
    ) {
        if (node.semanticsId != 0) {
            map[node.semanticsId] = node
        }
        node.zSortedChildren.forEach {
            populateSemanticsIdToLayoutNodeMap(it, map)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
    private fun SemanticsNode.toScannableView(window: ComposeWindow): ScannableView {
        val layoutNode = window.layoutNodes[this.id] ?: return ScannableView.IgnoredComposeView
        // this is a somewhat expensive call, so avoid calling it multiple times
        val config = this.config
        val captureIgnoreSubTree = config.getOrNull(CaptureModifier.CaptureIgnore)
        val isVisible = !config.contains(SemanticsProperties.InvisibleToUser)
        val notAttachedOrPlaced = !layoutNode.isPlaced || !layoutNode.isAttached
        val semanticType =
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
                config.toReplayType()
            }

        // Handle hybrid interop AndroidViews inside Compose elements. Must test the semantic type:
        // in an overlay window the adjusted type is TransparentView, and gating on that would drop
        // the embedded view's entire subtree.
        val interopAndroidView = layoutNode.getInteropView()
        if (semanticType == ReplayType.View && interopAndroidView != null) {
            return ScannableView.AndroidView(
                view = interopAndroidView,
                skipReplayComposeViews = false,
            )
        }

        val type = semanticType.occlusionAdjustedFor(window, config)
        val nodeBounds = this.unclippedGlobalBounds

        return ScannableView.ComposeView(
            replayRect =
                ReplayRect(
                    type = type,
                    // Add window offset to translate from window-relative to screen coordinates
                    x = nodeBounds.left.toInt() + window.offsetX,
                    y = nodeBounds.top.toInt() + window.offsetY,
                    width = nodeBounds.width.toInt(),
                    height = nodeBounds.height.toInt(),
                ),
            // The display name is not really used for anything
            displayName = "ComposeView",
            children = this.children.asSequence().map { it.toScannableView(window) },
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

    /**
     * Whether this window is a floating overlay, judged from its root's background rather than by
     * scanning the semantics tree for IsDialog/IsPopup. An activity window paints an opaque
     * backdrop; a Dialog, Popup or ModalBottomSheet window does not.
     *
     * This reads one background instead of walking every node. SemanticsNode.children rebuilds a
     * list on each access, so the scan it replaces cost a second traversal *and* a list allocation
     * per node, on the main thread inside parseDuration.
     */
    private fun View.isOverlayWindowRoot(): Boolean = BackgroundOpacity.paintedType(this) != ReplayType.View

    /**
     * Downgrades a generic container inside an overlay window. Compose semantics carry no background
     * information, and guessing opaque lets a full-screen container occlude the windows beneath it.
     */
    private fun ReplayType.occlusionAdjustedFor(
        window: ComposeWindow,
        config: SemanticsConfiguration,
    ): ReplayType =
        if (window.isOverlay && this == ReplayType.View && !config.paintsSurface) {
            ReplayType.TransparentView
        } else {
            this
        }

    /**
     * Whether this node draws a surface of its own, which an overlay's scrim and wrappers do not.
     * Matched by key name because [SemanticsProperties] gained `Shape` after the Compose version
     * this module compiles against; on older runtimes it is simply absent.
     */
    private val SemanticsConfiguration.paintsSurface: Boolean
        // TODO(@murki): Migrate to using strongly-typed [SemanticsProperties.Shape] check when updating compose-ui to >= 1.9.0
        get() = any { it.key.name == SHAPE_KEY }

    /** State fixed for the duration of one [parse] call. */
    private class ComposeWindow(
        val offsetX: Int,
        val offsetY: Int,
        val layoutNodes: MutableIntObjectMap<LayoutNode>,
        val isOverlay: Boolean,
    )

    private val SemanticsNode.unclippedGlobalBounds: Rect
        get() {
            return Rect(positionInWindow, size.toSize())
        }
}
