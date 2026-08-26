// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.graphics.Rect
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Which device the replay geometry expectations belong to.
 *
 * Most of the replay suite asserts exact pixel rects, which only hold at the density they were
 * captured on. CI runs a Nexus 6 emulator; developers run whatever phone is plugged in. Select with:
 *
 * ```
 * -Pandroid.testInstrumentationRunnerArguments.replayDevice=nexus6   # CI baseline (default)
 * -Pandroid.testInstrumentationRunnerArguments.replayDevice=pixel10  # local hardware
 * -Pandroid.testInstrumentationRunnerArguments.replayDevice=auto     # detect from display bounds
 * ```
 *
 * [hasReferenceGeometry] is true only for the profile the hardcoded rects were calibrated on. Tests
 * that assert exact coordinates gate on it and are *skipped* elsewhere, so a local run reports
 * honest skips instead of two dozen density failures that hide real regressions.
 */
enum class ReplayDeviceProfile(
    val label: String,
    val widthPx: Int,
    val heightPx: Int,
    /** Whether the suite's hardcoded pixel expectations were captured on this device. */
    val hasReferenceGeometry: Boolean,
) {
    NEXUS_6("Nexus 6 (CI emulator)", 1440, 2560, hasReferenceGeometry = true),
    PIXEL_10("Pixel 10", 1080, 2424, hasReferenceGeometry = false),
    ;

    companion object {
        private const val ARG = "replayDevice"

        /** Live display bounds, i.e. what [DisplayManagers] will emit as the first replay rect. */
        fun deviceBounds(): Rect =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getSystemService(WindowManager::class.java)
                .currentWindowMetrics
                .bounds

        private fun detect(): ReplayDeviceProfile? {
            val bounds = deviceBounds()
            return entries.firstOrNull { it.widthPx == bounds.width() && it.heightPx == bounds.height() }
        }

        /**
         * The selected profile. Defaults to [NEXUS_6] so CI keeps asserting exactly what it always
         * has. An explicit profile that does not match the attached device fails loudly rather than
         * silently asserting the wrong numbers.
         */
        fun current(): ReplayDeviceProfile {
            val requested = InstrumentationRegistry.getArguments().getString(ARG)?.lowercase()
            val profile =
                when (requested) {
                    null, "nexus6" -> NEXUS_6
                    "pixel10" -> PIXEL_10
                    "auto" ->
                        detect() ?: error(
                            "replayDevice=auto could not match display ${deviceBounds().width()}x" +
                                "${deviceBounds().height()} to a known profile; add one to ReplayDeviceProfile",
                        )
                    else -> error("Unknown replayDevice='$requested'. Use nexus6, pixel10 or auto.")
                }
            val bounds = deviceBounds()
            check(profile.widthPx == bounds.width() && profile.heightPx == bounds.height()) {
                "replayDevice=${profile.name.lowercase()} expects ${profile.widthPx}x${profile.heightPx} " +
                    "but the attached device is ${bounds.width()}x${bounds.height()}. " +
                    "Pass -Pandroid.testInstrumentationRunnerArguments.replayDevice=auto to detect it."
            }
            return profile
        }
    }
}
