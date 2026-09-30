// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import android.view.View
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.internal.mappers.BackgroundOpacity

internal class ViewTypeResolver(
    sessionReplayConfiguration: SessionReplayConfiguration,
    private val externalMapper: Map<ReplayType, List<String>>? =
        sessionReplayConfiguration.categorizers,
) {
    /**
     * The type to report for [view], or null when no class-name mapping applies and the generic
     * mappers should run instead.
     *
     * A type the host app declared through [SessionReplayConfiguration.categorizers] is the
     * caller's choice and is returned as-is. A built-in generic container is refined by what the
     * view actually paints, since it only occludes with an opaque background.
     */
    fun resolve(
        className: String,
        view: View,
    ): ReplayType? {
        declared[className]?.let { return it }
        val builtInType = builtIn[className] ?: return null
        return if (builtInType == ReplayType.View) {
            BackgroundOpacity.paintedType(view) ?: ReplayType.TransparentView
        } else {
            builtInType
        }
    }

    private val declared: Map<String, ReplayType> by lazy { externalMapper.flattenByClassName() }

    private val builtIn: Map<String, ReplayType> by lazy { defaultMapper.flattenByClassName() }

    private fun Map<ReplayType, List<String>>?.flattenByClassName(): Map<String, ReplayType> =
        this
            ?.flatMap { (type, viewNames) -> viewNames.map { it to type } }
            ?.toMap()
            .orEmpty()

    private val defaultMapper: Map<ReplayType, List<String>> =
        mapOf(
            ReplayType.View to
                listOf(
                    "View",
                    "DecorView",
                    "ViewStub",
                    "ComposeView",
                    "CircleView",
                    "FloatingBarView",
                    "MaterialCardView",
                    // Compose (Foundation)
                    "AndroidView",
                    "Box",
                    "Surface",
                    "Row",
                    "Column",
                ),
            ReplayType.BackgroundImage to emptyList(),
            ReplayType.Chevron to emptyList(),
            ReplayType.Ignore to
                listOf(
                    "ContentFrameLayout",
                    "FitWindowsLinearLayout",
                ),
            ReplayType.Image to
                listOf(
                    "ImageView",
                    "AppCompatImageView",
                    "AsyncImage",
                    // Compose (Foundation)
                    "Image",
                    "Icon",
                ),
            ReplayType.Label to
                listOf(
                    // Compose (Foundation)
                    "BasicText",
                    "Text",
                ),
            ReplayType.Button to
                listOf(
                    // Compose (Foundation)
                    "ClickableText",
                    // Compose (Material)
                    "Button",
                    "IconButton",
                    "TextButton",
                ),
            ReplayType.SwitchOff to
                listOf(
                    // Compose (Foundation)
                    "Checkbox",
                ),
            ReplayType.Keyboard to emptyList(),
            ReplayType.Map to
                listOf(
                    "MapView",
                ),
            ReplayType.TextInput to
                listOf(
                    "TextInputEditText",
                    // Compose (Foundation)
                    "TextField",
                ),
            ReplayType.TransparentView to emptyList(),
            ReplayType.WebView to
                listOf(
                    "WebView",
                ),
        )
}
