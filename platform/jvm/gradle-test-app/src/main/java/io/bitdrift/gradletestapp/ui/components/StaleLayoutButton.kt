// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import timber.log.Timber
import java.lang.reflect.Field

/**
 * A custom button that simulates a stale layout scenario
 */
@SuppressLint("SetTextI18n")
class StaleLayoutButton
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyle,
) : AppCompatButton(context, attrs, defStyleAttr) {
    private var hasSetup = false

    init {
        text = "First Line\nSecond Line\nThird Line"
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)

        if (!hasSetup && layout != null) {
            hasSetup = true
            modifyTextLengthAfterRendering()
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun modifyTextLengthAfterRendering() {
        runCatching {
            val textViewClass = Class.forName("android.widget.TextView")
            val mTextField: Field = textViewClass.getDeclaredField("mText")
            mTextField.isAccessible = true

            mTextField.set(this, "Short")

        }.getOrElse {
            Timber.tag("StaleButton").e("Failed to create stale layout button")
        }
    }

}
