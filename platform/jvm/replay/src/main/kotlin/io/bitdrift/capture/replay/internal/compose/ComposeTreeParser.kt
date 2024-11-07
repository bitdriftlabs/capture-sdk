// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.replay.internal.compose

import android.view.View
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.toSize
import io.bitdrift.capture.replay.ReplayModule
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.internal.ReplayRect
import io.bitdrift.capture.replay.internal.ScannableView

internal object ComposeTreeParser {
    internal val View.mightBeComposeView: Boolean
        get() = this is AndroidComposeView

    internal fun parse(androidComposeView: View): ScannableView {
        val semanticsOwner = if (androidComposeView is AndroidComposeView) {
            androidComposeView.semanticsOwner
        } else {
            ReplayModule.L.e(null, "View passed to ComposeTreeParser.parse() is not an AndroidComposeView. view=${androidComposeView.javaClass.name}")
            return ScannableView.IgnoredComposeView
        }
        val rootNode = semanticsOwner.unmergedRootSemanticsNode
        ReplayModule.L.d("Found Compose SemanticsNode root. Parsing Compose tree.")
        return rootNode.toScannableView()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun SemanticsNode.toScannableView(): ScannableView.ComposeView {
        val notAttachedOrPlaced = !this.layoutNode.isPlaced || !this.layoutNode.isAttached
        val isVisible = !this.isTransparent && !unmergedConfig.contains(SemanticsProperties.InvisibleToUser)
        val type = if (notAttachedOrPlaced) {
            return ScannableView.IgnoredComposeView
        } else if (!isVisible) {
            ReplayType.TransparentView
        } else {
            this.unmergedConfig.toReplayType()
        }
        return ScannableView.ComposeView(
            replayRect = ReplayRect(
                type = type,
                x = this.unclippedGlobalBounds.left.toInt(),
                y = this.unclippedGlobalBounds.top.toInt(),
                width = this.unclippedGlobalBounds.width.toInt(),
                height = this.unclippedGlobalBounds.height.toInt(),
            ),
            // The display name is not really used for anything
            displayName = "ComposeView",
            children = this.children.asSequence().map { it.toScannableView() },
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
