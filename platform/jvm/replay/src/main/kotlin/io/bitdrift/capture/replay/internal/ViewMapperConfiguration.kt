// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
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
    val mapper: ScatterMap<String, ReplayType> by lazy {
        val map = MutableScatterMap<String, ReplayType>()
        defaultMapper.forEach { type, viewNames ->
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
        map
    }

    // TODO(murki): Clean-up the list below for Compose and provide a Modifier to let users mark things a types instead.
    private val defaultMapper: ScatterMap<ReplayType, List<String>> =
        MutableScatterMap<ReplayType, List<String>>().apply {
            put(
                ReplayType.View,
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
            )
            put(ReplayType.BackgroundImage, emptyList())
            put(ReplayType.Chevron, emptyList())
            put(
                ReplayType.Ignore,
                listOf(
                    "ContentFrameLayout",
                    "FitWindowsLinearLayout",
                ),
            )
            put(
                ReplayType.Image,
                listOf(
                    "ImageView",
                    "AppCompatImageView",
                    "AsyncImage",
                    // Compose (Foundation)
                    "Image",
                    "Icon",
                ),
            )
            put(
                ReplayType.Label,
                listOf(
                    // Compose (Foundation)
                    "BasicText",
                    "Text",
                ),
            )
            put(
                ReplayType.Button,
                listOf(
                    // Compose (Foundation)
                    "ClickableText",
                    // Compose (Material)
                    "Button",
                    "IconButton",
                    "TextButton",
                ),
            )
            put(
                ReplayType.SwitchOff,
                listOf(
                    // Compose (Foundation)
                    "Checkbox", // TODO(murki): Figure how to handle on/off state for Compose
                ),
            )
            put(ReplayType.Keyboard, emptyList())
            put(
                ReplayType.Map,
                listOf(
                    "MapView",
                ),
            )
            put(
                ReplayType.TextInput,
                listOf(
                    "TextInputEditText",
                    // Compose (Foundation)
                    "TextField",
                ),
            )
            put(ReplayType.TransparentView, emptyList())
            put(ReplayType.WebView, emptyList())
        }
}
