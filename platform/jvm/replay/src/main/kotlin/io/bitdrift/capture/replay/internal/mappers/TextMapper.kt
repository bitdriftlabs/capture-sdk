// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal.mappers

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.View.TEXT_ALIGNMENT_CENTER
import android.view.View.TEXT_ALIGNMENT_GRAVITY
import android.view.View.TEXT_ALIGNMENT_TEXT_END
import android.view.View.TEXT_ALIGNMENT_TEXT_START
import android.view.View.TEXT_ALIGNMENT_VIEW_END
import android.view.View.TEXT_ALIGNMENT_VIEW_START
import android.widget.TextView
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.internal.ReplayRect

internal data class TextMapperConfig(
    val showViewOutline: Boolean,
    val interlinePadding: Int,
)

internal class TextMapper(
    private val textMapperConfig: TextMapperConfig = TextMapperConfig(false, interlinePadding = 4),
) : Mapper() {
    override fun map(view: View): MutableList<ReplayRect> {
        val list = super.map(view)

        if (view is TextView) {
            val bounds = Rect()
            if (textMapperConfig.showViewOutline) {
                addView(view = view, list = list)
            }

            var lineY = 0f
            if (view.width > 0 &&
                view.height > 0 &&
                view.text.isNotEmpty() &&
                view.layout != null
            ) {
                // Calculate the maximum height of the whole string
                view.paint.getTextBounds(view.text.toString(), 0, view.text.length, bounds)
                val maxLineHeight = bounds.height()

                // Estimate the total height of all the lines for vertical centering
                val totalHeight = (view.lineSpacingExtra + textMapperConfig.interlinePadding + maxLineHeight) * view.layout.lineCount

                // Cut a multiline wrapped text into multiple ReplayRect Labels
                for (lineNumber in 0 until view.layout.lineCount) {
                    val lineText = retrieveLine(view, lineNumber)

                    // Retrieve the boundaries of this line of text
                    view.paint.getTextBounds(lineText, 0, lineText.length, bounds)

                    if (lineText.trim().isNotEmpty()) {
                        // Adjust to screen coordinates and alignment
                        lineY += bounds.height()
                        alignHorizontal(view = view, bounds = bounds)
                        alignVertical(view = view, bounds = bounds, totalLinesHeight = totalHeight.toInt())
                        alignInScreen(bounds)
                        list.add(ReplayRect(ReplayType.Label, bounds.left, bounds.top + lineY.toInt(), bounds.width(), bounds.height()))
                    } else {
                        // Adding a blank line
                        lineY += maxLineHeight
                    }
                    lineY += view.lineSpacingExtra + textMapperConfig.interlinePadding
                }

                // add text Drawables as icons
                addDrawables(view, list)
            }
        }
        return list
    }

    private fun addDrawables(
        view: TextView,
        list: MutableList<ReplayRect>,
    ) {
        // start
        view.compoundDrawables[0]?.let { drawableStart ->
            val bounds = Rect(drawableStart.bounds)
            if (!bounds.isEmpty) {
                bounds.offset(view.paddingStart, ((view.height - bounds.height()) / 2))
                alignInScreen(bounds = bounds)
                list.add(ReplayRect(ReplayType.Image, bounds.left, bounds.top, bounds.width(), bounds.height()))
            }
        }

        // top
        view.compoundDrawables[1]?.let { drawableTop ->
            val bounds = Rect(drawableTop.bounds)
            if (!bounds.isEmpty) {
                bounds.offset((view.width - bounds.width()) / 2, view.paddingTop)
                alignInScreen(bounds = bounds)
                list.add(ReplayRect(ReplayType.Image, bounds.left, bounds.top, bounds.width(), bounds.height()))
            }
        }

        // end
        view.compoundDrawables[2]?.let { drawableEnd ->
            val bounds = Rect(drawableEnd.bounds)
            if (!bounds.isEmpty) {
                bounds.offset(view.width - view.paddingEnd - bounds.width(), ((view.height - bounds.height()) / 2))
                alignInScreen(bounds = bounds)
                list.add(ReplayRect(ReplayType.Image, bounds.left, bounds.top, bounds.width(), bounds.height()))
            }
        }

        // bottom
        view.compoundDrawables[3]?.let { drawableBottom ->
            val bounds = Rect(drawableBottom.bounds)
            if (!bounds.isEmpty) {
                bounds.offset((view.width - bounds.width()) / 2, view.height - view.paddingBottom - bounds.height())
                alignInScreen(bounds = bounds)
                list.add(ReplayRect(ReplayType.Image, bounds.left, bounds.top, bounds.width(), bounds.height()))
            }
        }
    }

    // Retrieve the text for the given line
    private fun retrieveLine(
        view: TextView,
        line: Int,
    ): String {
        val lineStart = view.layout.getLineStart(line)
        val lineEnd = view.layout.getLineEnd(line)
        return view.layout.text.substring(lineStart, lineEnd)
    }

    @SuppressLint("SwitchIntDef", "RtlHardcoded")
    private fun alignHorizontal(
        view: TextView,
        alignment: Int = view.textAlignment,
        gravity: Int = view.gravity,
        bounds: Rect,
    ) {
        when (alignment) {
            TEXT_ALIGNMENT_GRAVITY -> {
                when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                    Gravity.LEFT -> bounds.offset(view.compoundPaddingStart, 0)
                    Gravity.RIGHT -> bounds.offset(view.width - bounds.width(), 0)
                    Gravity.CENTER_HORIZONTAL ->
                        bounds.offset(
                            (view.width + view.compoundPaddingStart - view.compoundPaddingEnd - bounds.width()) / 2,
                            0,
                        )
                }
            }
            TEXT_ALIGNMENT_TEXT_START,
            TEXT_ALIGNMENT_VIEW_START,
            -> bounds.offset(view.compoundPaddingStart, 0)
            TEXT_ALIGNMENT_CENTER -> bounds.offset((view.width - bounds.width()) / 2, 0)
            TEXT_ALIGNMENT_TEXT_END,
            TEXT_ALIGNMENT_VIEW_END,
            -> bounds.offset(view.width - bounds.width(), 0)
        }
    }

    @SuppressLint("SwitchIntDef")
    private fun alignVertical(
        view: TextView,
        alignment: Int = view.textAlignment,
        gravity: Int = view.gravity,
        bounds: Rect,
        totalLinesHeight: Int,
    ) {
        when (alignment) {
            TEXT_ALIGNMENT_GRAVITY -> {
                when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                    Gravity.TOP -> bounds.offset(0, view.compoundPaddingTop)
                    Gravity.CENTER_VERTICAL ->
                        bounds.offset(
                            0,
                            (view.height + view.compoundPaddingTop - view.compoundPaddingBottom - totalLinesHeight) / 2,
                        )
                    Gravity.BOTTOM -> bounds.offset(0, view.height - totalLinesHeight)
                }
            }
            TEXT_ALIGNMENT_CENTER -> bounds.offset(0, ((view.height - totalLinesHeight) / 2))
        }
    }
}
