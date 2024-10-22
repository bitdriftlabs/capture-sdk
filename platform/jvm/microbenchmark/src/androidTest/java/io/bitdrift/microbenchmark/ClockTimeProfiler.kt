// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val LOG_MESSAGE = "50 characters long test message - 0123456789012345"

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop.
 * In microbenchmarks the number of iterations to be executed is determined by the library itself
 */
@RunWith(AndroidJUnit4::class)
class ClockTimeProfiler {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private fun startLogger() {
        Capture.Logger.start(
            apiKey = "android-benchmark-test",
            apiUrl = "https://api-tests.bitdrift.io".toHttpUrl(),
            sessionStrategy = SessionStrategy.Fixed(),
        )
    }

    @Test
    fun loggerStart() {
        benchmarkRule.measureRepeated {
            LoggerImpl(
                apiKey = "android-benchmark-test",
                apiUrl = "https://api-tests.bitdrift.io".toHttpUrl(),
                fieldProviders = listOf(),
                dateProvider = SystemDateProvider(),
                configuration = Configuration(),
                sessionStrategy = SessionStrategy.Fixed(),
            )
        }
    }

    @Test
    fun logNotMatchedNoFields() {
        startLogger()
        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo { LOG_MESSAGE }
        }
    }

    @Test
    fun logNotMatched5Fields() {
        startLogger()
        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo(
                mapOf(
                    "keykeykey1" to "valvalval1",
                    "keykeykey2" to "valvalval2",
                    "keykeykey3" to "valvalval3",
                    "keykeykey4" to "valvalval4",
                    "keykeykey5" to "valvalval5",
                )
            ) { LOG_MESSAGE }
        }
    }

    @Test
    fun logNotMatched10Fields() {
        startLogger()
        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo(
                mapOf(
                    "keykeykey1" to "valvalval1",
                    "keykeykey2" to "valvalval2",
                    "keykeykey3" to "valvalval3",
                    "keykeykey4" to "valvalval4",
                    "keykeykey5" to "valvalval5",
                    "keykeykey6" to "valvalval6",
                    "keykeykey7" to "valvalval7",
                    "keykeykey8" to "valvalval8",
                    "keykeykey9" to "valvalval9",
                    "keykeykey10" to "valvalval10",
                )
            ) { LOG_MESSAGE }
        }
    }
}
