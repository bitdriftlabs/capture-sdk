// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal.mappers

import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageButton
import androidx.appcompat.widget.SwitchCompat
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.internal.ReplayRect

internal data class ButtonMapperConfig(
    val paddingStart: Int,
    val paddingTop: Int,
    val paddingEnd: Int,
    val paddingBottom: Int,
)

internal data class SwitchConfig(
    val paddingStart: Int,
    val widthRatio: Int,
    val heightRatio: Int,
)

// Map a view implementing android.widget.Button to a ReplayRect
internal class ButtonMapper(
    private val buttonMapperConfig: ButtonMapperConfig = ButtonMapperConfig(8, 8, 8, 8),
    private val switchConfig: SwitchConfig = SwitchConfig(paddingStart = 5, widthRatio = 2, heightRatio = 1),
) : Mapper() {
    override fun map(view: View): MutableList<ReplayRect> {
        val list = super.map(view)
        when (view) {
            is SwitchCompat -> {
                // SwitchCompat is a CompoundButton but uses thumbDrawable instead of ButtonDrawable
                view.thumbDrawable?.bounds?.let { bounds ->
                    // Replay Switches are rectangles, while Android Switches are square, we multiply x2 the width to have something that
                    // looks similar
                    val width = bounds.width() * switchConfig.widthRatio
                    val height = bounds.height() * switchConfig.heightRatio

                    if (view.isChecked) {
                        val offset = bounds.left - bounds.width() - view.switchPadding + switchConfig.paddingStart
                        addButton(ReplayType.SwitchOn, viewOriginX + offset, viewOriginY + bounds.top, width, height, list)
                    } else {
                        val offset = bounds.left + view.switchPadding - switchConfig.paddingStart
                        addButton(ReplayType.SwitchOff, viewOriginX + offset, viewOriginY + bounds.top, width, height, list)
                    }
                }
            }
            is ImageButton -> {
                addView(ReplayType.Image, view, list)
            }
            is CompoundButton -> {
                for (drawable in view.compoundDrawables) {
                    drawable?.bounds?.let { bounds ->
                        addButton(
                            ReplayType.Image,
                            viewOriginX + bounds.left,
                            viewOriginY + bounds.top,
                            bounds.width(),
                            bounds.height(),
                            list,
                        )
                    }
                }
                view.buttonDrawable?.bounds?.let { bounds ->
                    val type =
                        if (view.isChecked) {
                            ReplayType.Button
                        } else {
                            ReplayType.View
                        }
                    addButton(type, viewOriginX + bounds.left, viewOriginY + bounds.top, bounds.width(), bounds.height(), list)
                }
            }
            is Button -> {
                addButton(ReplayType.Button, viewOriginX, viewOriginY, view.width, view.height, list)
            }
        }
        return list
    }

    private fun addButton(
        type: ReplayType,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        list: MutableList<ReplayRect>,
    ) {
        list.add(
            ReplayRect(
                type,
                left + buttonMapperConfig.paddingStart,
                top + buttonMapperConfig.paddingTop,
                width - buttonMapperConfig.paddingStart - buttonMapperConfig.paddingEnd,
                height - buttonMapperConfig.paddingTop - buttonMapperConfig.paddingBottom,
            ),
        )
    }
}
