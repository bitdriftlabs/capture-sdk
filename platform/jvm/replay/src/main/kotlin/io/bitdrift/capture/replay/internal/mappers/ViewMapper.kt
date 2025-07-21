// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal.mappers

import android.content.res.Resources
import android.view.View
import androidx.core.content.res.ResourcesCompat
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.SessionReplayController
import io.bitdrift.capture.replay.internal.ReplayRect
import io.bitdrift.capture.replay.internal.ScannableView
import io.bitdrift.capture.replay.internal.ViewMapperConfiguration
import androidx.core.view.isVisible

internal class ViewMapper(
    sessionReplayConfiguration: SessionReplayConfiguration,
    private val viewMapperConfiguration: ViewMapperConfiguration = ViewMapperConfiguration(sessionReplayConfiguration),
    private val buttonMapper: ButtonMapper = ButtonMapper(),
    private val textMapper: TextMapper = TextMapper(),
    private val backgroundMapper: BackgroundMapper = BackgroundMapper(),
) {
    fun updateMetrics(
        node: ScannableView,
        replayCaptureMetrics: ReplayCaptureMetrics,
    ) = when (node) {
        is ScannableView.AndroidView -> {
            replayCaptureMetrics.viewCount += 1
        }
        is ScannableView.ComposeView -> {
            replayCaptureMetrics.composeViewCount += 1
        }
    }

    fun viewIsVisible(node: ScannableView): Boolean {
        return when (node) {
            is ScannableView.AndroidView -> {
                (node.view.isVisible) && (node.view.width > 0) && (node.view.height > 0)
            }
            is ScannableView.ComposeView -> {
                return node !is ScannableView.IgnoredComposeView
            }
        }
    }

    /**
     * Matches Android views and compose views to the corresponding Bitdrift Type.
     */
    fun mapView(node: ScannableView): List<ReplayRect> =
        when (node) {
            is ScannableView.AndroidView -> {
                node.view.viewToReplayRect()
            }
            is ScannableView.ComposeView -> {
                listOf(node.replayRect)
            }
        }

    private fun View.viewToReplayRect(): List<ReplayRect> {
        val list = mutableListOf<ReplayRect>()
        val resourceName =
            if (isValidResId(this.id)) {
                try {
                    resources.getResourceEntryName(this.id)
                } catch (ignore: Resources.NotFoundException) {
                    // Do nothing.
                    SessionReplayController.L.e(ignore, "Ignoring view due to:${ignore.message} for ${this.id}")
                    "Failed to retrieve ID"
                }
            } else {
                "invalid_resource_id"
            }

        val type = viewMapperConfiguration.mapper[this.javaClass.simpleName]
        if (type == null) {
            // Try to use generic mapper
            list.addAll(buttonMapper.map(this))
            list.addAll(textMapper.map(this))
            list.addAll(backgroundMapper.map(this))
            if (list.isEmpty()) {
                SessionReplayController.L.v(
                    "Ignoring Unknown view: $resourceName ${this.javaClass.simpleName}:" +
                        " w=${this.width}, h=${this.height}",
                )
            } else {
                SessionReplayController.L.v("Matched ${list.size} views with ButtonMapper and TextMapper and BackgroundMapper")
            }
        } else {
            val out = IntArray(2)
            this.getLocationOnScreen(out)
            SessionReplayController.L.v(
                "Successfully mapped Android view=${this.javaClass.simpleName} to=$type:" +
                    " ${out[0]}, ${out[1]}, ${this.width}, ${this.height}",
            )
            list.add(ReplayRect(type, out[0], out[1], this.width, this.height))
        }
        return list
    }

    private fun isValidResId(resId: Int): Boolean {
        @Suppress("MaxLineLength")
        // the checks below are to avoid spamming logcat with errors emitted by the Android sub-system when calling resources.getResourceEntryName(invalidResourceId)
        // see: https://cs.android.com/android/platform/superproject/main/+/6adcde7195b0c9efd344a2bec2412909ab66d047:frameworks/base/libs/androidfw/AssetManager2.cpp;l=657
        // lifted from: https://cs.android.com/android/platform/superproject/main/+/a2a9539615425091c7413249e5dc063009cf222b:frameworks/base/libs/androidfw/include/androidfw/ResourceUtils.h;l=62
        return resId != View.NO_ID &&
            resId != ResourcesCompat.ID_NULL &&
            (resId.toUInt() and 0x00ff0000u) != 0u &&
            (resId.toUInt() and 0xff000000u) != 0u
    }
}
