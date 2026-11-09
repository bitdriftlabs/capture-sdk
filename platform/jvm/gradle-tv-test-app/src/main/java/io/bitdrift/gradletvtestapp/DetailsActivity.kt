// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.bitdrift.capture.Capture

class DetailsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.details_title)
        setContentView(buildContentView())

        Capture.Logger.logInfo { "Details screen opened" }
    }

    private fun buildContentView(): LinearLayout {
        val spacing = resources.getDimensionPixelSize(R.dimen.screen_spacing)

        val titleView = TextView(this).apply {
            text = getString(R.string.details_title)
            textSize = 28f
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val descriptionView = TextView(this).apply {
            text = getString(R.string.details_description)
            textSize = 18f
        }

        val backButton = Button(this).apply {
            text = getString(R.string.back_to_main)
            isAllCaps = false
            textSize = 20f
            minHeight = resources.getDimensionPixelSize(R.dimen.button_height)
            setOnClickListener {
                Capture.Logger.logInfo { "Returning to main screen" }
                finish()
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(spacing, spacing, spacing, spacing)
            addView(titleView, layoutParams(spacing))
            addView(descriptionView, layoutParams(spacing))
            addView(backButton, layoutParams(0))
        }
    }

    private fun layoutParams(bottomMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            this.bottomMargin = bottomMargin
        }
}
