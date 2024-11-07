// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal.mappers

import android.content.res.Resources
import android.view.View
import io.bitdrift.capture.replay.ReplayManager
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.internal.EncodedScreenMetrics
import io.bitdrift.capture.replay.internal.ReplayRect
import io.bitdrift.capture.replay.internal.ScannableView
import io.bitdrift.capture.replay.internal.ViewMapperConfiguration

internal class ViewMapper(
    sessionReplayConfiguration: SessionReplayConfiguration,
    private val viewMapperConfiguration: ViewMapperConfiguration = ViewMapperConfiguration(sessionReplayConfiguration),
    private val buttonMapper: ButtonMapper = ButtonMapper(),
    private val textMapper: TextMapper = TextMapper(),
    private val backgroundMapper: BackgroundMapper = BackgroundMapper(),
) {

    fun updateMetrics(node: ScannableView, encodedScreenMetrics: EncodedScreenMetrics) {
        return when (node) {
            is ScannableView.AndroidView -> {
                encodedScreenMetrics.viewCount += 1
            }
            is ScannableView.ComposeView -> {
                encodedScreenMetrics.composeViewCount += 1
            }
        }
    }

    fun viewIsVisible(node: ScannableView): Boolean {
        return when (node) {
            is ScannableView.AndroidView -> {
                (node.view.visibility == View.VISIBLE) && (node.view.width > 0) && (node.view.height > 0)
            }
            is ScannableView.ComposeView -> {
                return node !is ScannableView.IgnoredComposeView
            }
        }
    }

    /**
     * Matches Android views and compose views to the corresponding Bitdrift Type.
     */
    fun mapView(
        node: ScannableView,
    ): List<ReplayRect> {
        return when (node) {
            is ScannableView.AndroidView -> {
                node.view.viewToReplayRect()
            }
            is ScannableView.ComposeView -> {
                listOf(node.replayRect)
            }
        }
    }

    private fun View.viewToReplayRect(): List<ReplayRect> {
        val list = mutableListOf<ReplayRect>()
        val resourceName = if (id != -1) {
            try {
                resources.getResourceEntryName(this.id)
            } catch (ignore: Resources.NotFoundException) {
                // Do nothing.
                ReplayManager.L.e(ignore, "Ignoring view due to:${ignore.message} for ${this.id}")
                "Failed to retrieve ID"
            }
        } else {
            "no_resource_id"
        }

        val type = viewMapperConfiguration.mapper[this.javaClass.simpleName]
        if (type == null) {
            // Try to use generic mapper
            list.addAll(buttonMapper.map(this))
            list.addAll(textMapper.map(this))
            list.addAll(backgroundMapper.map(this))
            if (list.isEmpty()) {
                ReplayManager.L.v(
                    "Ignoring Unknown view: $resourceName ${this.javaClass.simpleName}:" +
                        " w=${this.width}, h=${this.height}",
                )
            } else {
                ReplayManager.L.v("Matched ${list.size} views with ButtonMapper and TextMapper and BackgroundMapper")
            }
        } else {
            val out = IntArray(2)
            this.getLocationOnScreen(out)
            ReplayManager.L.v(
                "Successfully mapped Android view=${this.javaClass.simpleName} to=$type:" +
                    " ${out[0]}, ${out[1]}, ${this.width}, ${this.height}",
            )
            list.add(ReplayRect(type, out[0], out[1], this.width, this.height))
        }
        return list
    }
}
