// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter

class LayoutCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageScaleType(android.widget.ImageView.ScaleType.CENTER_CROP)
            setInfoAreaBackgroundColor(0xFF0F172A.toInt())
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val example = item as? LayoutExample ?: return
        val cardView = viewHolder.view as ImageCardView
        cardView.titleText = example.title
        cardView.contentText = example.summary
        cardView.mainImage = ColorDrawable(example.accentColor)
        cardView.setMainImageDimensions(
            dp(cardView, example.cardWidthDp),
            dp(cardView, example.cardHeightDp),
        )
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit

    private fun dp(cardView: ImageCardView, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            cardView.resources.displayMetrics,
        ).toInt()
}
