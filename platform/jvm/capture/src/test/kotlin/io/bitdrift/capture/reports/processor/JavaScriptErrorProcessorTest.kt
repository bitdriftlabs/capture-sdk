// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.common.truth.Truth.assertThat
import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.FrameType
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import org.junit.Test
import java.nio.ByteBuffer

class JavaScriptErrorProcessorTest {
    @Test
    fun parseCompleteStackTrace() {
        val rawStackTrace =
            """
            Error: Triggered Global JS Error - Intentional for testing
                at triggerGlobalJsError (http://localhost:8081/index.bundle//&platform=android&dev=true&lazy=true&minify=false&app=com.bitdrift.io.expoExample&modulesOnly=false&runModule=true&excludeSource=true&sourcePaths=url-server&transform.routerRoot=src%2Fapp&transform.engine=hermes&transform.bytecode=1&unstable_transformProfile=hermes-stable:118064:20)
                at _performTransitionSideEffects (http://localhost:8081/index.bundle//&platform=android&dev=true&lazy=true&minify=false&app=com.bitdrift.io.expoExample&modulesOnly=false&runModule=true&excludeSource=true&sourcePaths=url-server&transform.routerRoot=src%2Fapp&transform.engine=hermes&transform.bytecode=1&unstable_transformProfile=hermes-stable:71447:22)
                at forEach (native)
                at anonymous (http://localhost:8081/index.bundle//&platform=android&dev=true&lazy=true&minify=false&app=com.bitdrift.io.expoExample&modulesOnly=false&runModule=true&excludeSource=true&sourcePaths=url-server&transform.routerRoot=src%2Fapp&transform.engine=hermes&transform.bytecode=1&unstable_transformProfile=hermes-stable:24237:42)
            """.trimIndent()

        val builder = FlatBufferBuilder(1024)
        val sdk = 0
        val appMetrics = 0
        val deviceMetrics = 0

        val reportOffset =
            JavaScriptErrorProcessor.getJavaScriptErrorReport(
                builder,
                sdk,
                appMetrics,
                deviceMetrics,
                rawStackTrace,
            )

        builder.finish(reportOffset)
        val buffer = ByteBuffer.wrap(builder.sizedByteArray())
        val report = Report.getRootAsReport(buffer)

        // Verify report type
        assertThat(report.type()).isEqualTo(ReportType.JavaScriptError)

        // Verify error
        assertThat(report.errorsLength()).isEqualTo(1)
        val error = report.errors(0)
        assertThat(error.name()).isEqualTo("Error")
        assertThat(error.reason()).isEqualTo("Triggered Global JS Error - Intentional for testing")

        // Verify frames
        assertThat(error.stackTraceLength()).isEqualTo(4)

        // Check first frame
        val frame0 = error.stackTrace(0)
        assertThat(frame0.type()).isEqualTo(FrameType.JavaScript)
        assertThat(frame0.symbolName()).isEqualTo("triggerGlobalJsError")
        assertThat(frame0.sourceFile().line()).isEqualTo(118064)
        assertThat(frame0.sourceFile().column()).isEqualTo(20)
        assertThat(frame0.jsBundlePath()).isEqualTo("/index.bundle")

        // Check second frame
        val frame1 = error.stackTrace(1)
        assertThat(frame1.symbolName()).isEqualTo("_performTransitionSideEffects")
        assertThat(frame1.sourceFile().line()).isEqualTo(71447)
        assertThat(frame1.sourceFile().column()).isEqualTo(22)

        // Check native frame
        val frame2 = error.stackTrace(2)
        assertThat(frame2.symbolName()).isEqualTo("forEach")
        assertThat(frame2.sourceFile().path()).isEqualTo("[native code]")

        // Check anonymous frame
        val frame3 = error.stackTrace(3)
        assertThat(frame3.symbolName()).isEqualTo("anonymous")
        assertThat(frame3.sourceFile().line()).isEqualTo(24237)
    }

    @Test
    fun parseSimpleError() {
        val rawStackTrace = "Error: Simple error message"

        val builder = FlatBufferBuilder(1024)
        val reportOffset =
            JavaScriptErrorProcessor.getJavaScriptErrorReport(
                builder,
                sdk = 0,
                appMetrics = 0,
                deviceMetrics = 0,
                rawStackTrace,
            )

        builder.finish(reportOffset)
        val buffer = ByteBuffer.wrap(builder.sizedByteArray())
        val report = Report.getRootAsReport(buffer)

        assertThat(report.type()).isEqualTo(ReportType.JavaScriptError)
        val error = report.errors(0)
        assertThat(error.name()).isEqualTo("Error")
        assertThat(error.reason()).isEqualTo("Simple error message")
        assertThat(error.stackTraceLength()).isEqualTo(0)
    }

    @Test
    fun parseBundlePath() {
        val rawStackTrace =
            """
            TypeError: Cannot read property
                at myFunction (http://localhost:8081/src/app/App.bundle//&params:100:15)
            """.trimIndent()

        val builder = FlatBufferBuilder(1024)
        val reportOffset =
            JavaScriptErrorProcessor.getJavaScriptErrorReport(
                builder,
                sdk = 0,
                appMetrics = 0,
                deviceMetrics = 0,
                rawStackTrace,
            )

        builder.finish(reportOffset)
        val buffer = ByteBuffer.wrap(builder.sizedByteArray())
        val report = Report.getRootAsReport(buffer)

        val frame = report.errors(0).stackTrace(0)
        assertThat(frame.jsBundlePath()).isEqualTo("/src/app/App.bundle")
        assertThat(frame.sourceFile().path()).isEqualTo("App.bundle")
    }
}
