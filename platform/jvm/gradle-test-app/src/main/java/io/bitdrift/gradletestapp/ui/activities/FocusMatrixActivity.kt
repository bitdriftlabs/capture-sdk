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
 * A second Activity that exists purely to exercise every way an app window can lose focus, so the
 * SDK's flush-on-focus-loss can be verified end to end.
 *
 * Reaching it is itself one of the cases under test (an Activity transition drops focus on the
 * activity being left). The rest are driven from here:
 *
 *  - **IME** — focusing the text field raises the keyboard, which takes window focus.
 *  - **Permission dialog** — a system dialog takes focus without the activity stopping. Uses
 *    READ_PHONE_STATE, which the app already declares: adding a permission purely for this would
 *    drag in a `uses-feature` hardware declaration and the lint that enforces it, for no benefit.
 *    Revoke it first so the dialog actually appears on repeat runs:
 *    `adb shell pm revoke io.bitdrift.gradletestapp android.permission.READ_PHONE_STATE`
 *  - **Rotation** — `adb shell settings put system user_rotation 1` recreates the activity.
 *  - **Recents / home / kill** — driven entirely over adb, no UI needed.
 *
 * Built with views rather than Compose on purpose: the point is to observe `ViewTreeObserver` window
 * focus with as little between the harness and the platform as possible, and this app already mixes
 * both so it is not out of place.
 *
 * Focus changes go through the same [LifecycleEventLogger] helper `MainActivity` uses, so a focus
 * change and the SDK's resulting flush interleave in one capture on the device clock. That is what
 * makes the assertion "focus dropped, then a flush happened" possible at all.
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

        // Tapping this raises the IME, which takes window focus without stopping the activity.
        root.addView(
            EditText(this).apply {
                id = ID_INPUT
                setHint(R.string.focus_matrix_input_hint)
            },
        )

        // A system permission dialog also takes focus without an activity stop. Already-granted
        // permissions return instantly with no dialog and therefore no focus loss, so revoke it
        // before testing (see the class doc).
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
        // Same helper MainActivity uses, so both activities emit one consistent focus line that the
        // logcat harness already parses.
        LifecycleEventLogger.onWindowFocusChanged(this, hasFocus)
    }

    private companion object {
        const val PADDING_PX = 48
        const val ID_INPUT = 1_001
        const val ID_REQUEST_PERMISSION = 1_002
    }
}
