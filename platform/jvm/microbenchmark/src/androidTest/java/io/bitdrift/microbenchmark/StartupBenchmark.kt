@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.CrashReporterState
import io.bitdrift.capture.reports.CrashReporterStatus
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

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
                crashReporterStatus = CrashReporterStatus(CrashReporterState.NotInitialized),
            )
        }
    }
}