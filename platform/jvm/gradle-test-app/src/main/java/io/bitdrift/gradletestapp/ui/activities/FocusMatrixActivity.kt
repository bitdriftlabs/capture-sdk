// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.activities

import android.Manifest
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.diagnostics.lifecycle.LifecycleEventLogger
import timber.log.Timber

/**
 * Exercises window-focus-loss cases for verifying the SDK's flush-on-focus-loss: reaching it is an
 * Activity transition and the button opens a permission dialog. The text field raises the IME,
 * which does not drop window focus (measured on Pixel 10 / API 37). Recents/home/rotation are
 * driven over adb.
 */
class FocusMatrixActivity : AppCompatActivity() {
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Timber.i("focus-matrix permission result granted=%s", granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        root.addView(
            TextView(this).apply {
                setText(R.string.focus_matrix_instructions)
            },
        )

        root.addView(
            EditText(this).apply {
                id = ID_INPUT
                setHint(R.string.focus_matrix_input_hint)
            },
        )

        // An already-granted permission shows no dialog (and so no focus loss); revoke first:
        // `adb shell pm revoke io.bitdrift.gradletestapp android.permission.READ_PHONE_STATE`
        root.addView(
            Button(this).apply {
                id = ID_REQUEST_PERMISSION
                setText(R.string.focus_matrix_request_permission)
                setOnClickListener { requestPermission.launch(Manifest.permission.READ_PHONE_STATE) }
            },
        )

        setContentView(root)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        LifecycleEventLogger.onWindowFocusChanged(this, hasFocus)
    }

    private companion object {
        const val PADDING_PX = 48
        const val ID_INPUT = 1_001
        const val ID_REQUEST_PERMISSION = 1_002
    }
}
