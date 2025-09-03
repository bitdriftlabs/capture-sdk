// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/**
 * Adapted from:
 *  https://github.com/block/radiography/blob/24d5bad1e07252f67b2070331dbfe2c2e0698d29/radiography/src/main/java/radiography/ScannableView.kt
 *
 * Copyright 2021 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.replay.internal

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.AndroidComposeView
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.internal.ScannableView.AndroidView
import io.bitdrift.capture.replay.internal.compose.ComposeTreeParser

/**
 * Represents a logic view that can be rendered as a node in the view tree.
 *
 * Can either be an actual Android [View] ([AndroidView]) or a grouping of Composables that roughly
 * represents the concept of a logical "view" ([ComposeView]).
 */
internal sealed class ScannableView {
    /** The string that be used to identify the type of the view in the rendered output. */
    abstract val displayName: String

    /** The children of this view. */
    abstract val children: Sequence<ScannableView>

    class AndroidView(
        val view: View,
        skipReplayComposeViews: Boolean,
    ) : ScannableView() {
        override val displayName: String get() = view::class.java.simpleName
        override val children: Sequence<ScannableView> =
            view.scannableChildren(skipReplayComposeViews)

        override fun toString(): String = "${AndroidView::class.java.simpleName}($displayName)"
    }

    open class ComposeView(
        val replayRect: ReplayRect,
        override val displayName: String,
        override val children: Sequence<ScannableView>,
    ) : ScannableView()

    object IgnoredComposeView : ComposeView(
        replayRect = ReplayRect(ReplayType.Ignore, 0, 0, 0, 0),
        displayName = "Ignored Compose View",
        children = emptySequence(),
    )
}

/** Reflectively tries to determine if Compose is on the classpath. */
private val isComposeAvailable by lazy(LazyThreadSafetyMode.PUBLICATION) {
    try {
        Class.forName("androidx.compose.ui.platform.AndroidComposeView")
        true
    } catch (_: Throwable) {
        false
    }
}

private val View.mightBeComposeView: Boolean
    get() = isComposeAvailable && this is AndroidComposeView

/**
 * Lazily builds a tree of ScannableView nodes which represent the view hierarchy rooted at this level
 * It recursively traverse each node that are Android Views, but the moment it finds a Compose View
 * it then uses [androidx.compose.ui.semantics.SemanticsOwner.unmergedRootSemanticsNode] to access
 * that entire sub-tree at once and stops traversing any further children
 */
private fun View.scannableChildren(skipReplayComposeViews: Boolean = false): Sequence<ScannableView> {
    return sequence {
        if (mightBeComposeView && !skipReplayComposeViews) {
            val composableViews = ComposeTreeParser.parse(this@scannableChildren)
            yield(composableViews)
            // We short-circuit the recursive tree-parsing here
            // Don't visit children ourselves, the compose parser will have done that.
            return@sequence
        }

        if (this@scannableChildren !is ViewGroup) return@sequence

        for (i in 0 until childCount) {
            // Child may be null, if children were removed by another thread after we captured the child
            // count. getChildAt returns null for invalid indices, it doesn't throw.
            val view = getChildAt(i) ?: continue
            yield(
                AndroidView(
                    view,
                    skipReplayComposeViews,
                ),
            )
        }
    }
}
