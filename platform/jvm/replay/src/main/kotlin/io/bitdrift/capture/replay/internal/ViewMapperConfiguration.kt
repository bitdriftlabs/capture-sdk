// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.SessionReplayConfiguration

internal class ViewMapperConfiguration(
    sessionReplayConfiguration: SessionReplayConfiguration,
    private val externalMapper: Map<ReplayType, List<String>>? =
        sessionReplayConfiguration.categorizers,
) {
    /**
     * Let a third party select the ReplayType for the given class name.
     */
    val mapper: Map<String, ReplayType> by lazy {
        val map = mutableMapOf<String, ReplayType>()
        for ((type, viewNames) in defaultMapper) {
            for (viewName in viewNames) {
                map[viewName] = type
            }
        }
        externalMapper?.let { externalMap ->
            for ((type, viewNames) in externalMap) {
                for (viewName in viewNames) {
                    map[viewName] = type
                }
            }
        }
        map.toMap()
    }

    // TODO(murki): Clean-up the list below for Compose and provide a Modifier to let users mark things a types instead.
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
                    "Checkbox", // TODO(murki): Figure how to handle on/off state for Compose
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
            ReplayType.WebView to emptyList(),
        )
}
