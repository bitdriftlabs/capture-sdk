// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.profiling

import android.util.Log
import io.bitdrift.capture.Capture.LOG_TAG
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashProfilingTest {
    @Test
    fun warnsThatBothKindsAreUnavailableBelowApi36() {
        for (mode in CrashProfilingMode.entries) {
            ShadowLog.clear()
            mode.warnIfUnsupportedApi(35)

            val warning = ShadowLog.getLogsForTag(LOG_TAG).single()
            assertThat(warning.type).isEqualTo(Log.WARN)
            assertThat(warning.msg)
                .contains("$mode mode on API 35", "ANR profiles require Android 16 (API 36)")
                .contains("OOM profiles require Android 17 (API 37)", "Crash profiling is disabled")
                .contains("ordinary fatal issue reporting is unaffected")
        }
    }

    @Test
    fun warnsThatOnlyAnrIsSupportedOnApi36() {
        for (mode in CrashProfilingMode.entries) {
            ShadowLog.clear()
            mode.warnIfUnsupportedApi(36)

            val warning = ShadowLog.getLogsForTag(LOG_TAG).single()
            assertThat(warning.type).isEqualTo(Log.WARN)
            assertThat(warning.msg)
                .contains("$mode mode on API 36", "only ANR profiles are supported")
                .contains("OOM profiles are disabled", "Android 17 (API 37)")
        }
    }

    @Test
    fun doesNotWarnWhenBothKindsAreSupported() {
        for (mode in CrashProfilingMode.entries) {
            for (apiLevel in listOf(37, 38)) {
                ShadowLog.clear()
                mode.warnIfUnsupportedApi(apiLevel)
                assertThat(ShadowLog.getLogsForTag(LOG_TAG)).isEmpty()
            }
        }
    }
}
