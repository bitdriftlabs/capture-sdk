// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import android.os.strictmode.Violation
import io.bitdrift.capture.reports.processor.IIssueReporterProcessor
import java.io.InputStream

class FakeIssueReporterProcessor : IIssueReporterProcessor {
    private var shouldThrowWhenProcessingJvmCrash = false

    fun shouldFailPersistingJvmCrash(shouldThrowWhenProcessingJvmCrash: Boolean) {
        this.shouldThrowWhenProcessingJvmCrash = shouldThrowWhenProcessingJvmCrash
    }

    fun reset() {
        this.shouldThrowWhenProcessingJvmCrash = false
    }

    override fun processAppExitReport(
        fatalIssueType: Byte,
        timestamp: Long,
        description: String?,
        traceInputStream: InputStream,
    ) {
        // no-op for now
    }

    override fun processJvmCrash(
        callerThread: Thread,
        throwable: Throwable,
        allThreads: Map<Thread, Array<StackTraceElement>>?,
    ) {
        if (shouldThrowWhenProcessingJvmCrash) {
            throw ProcessJvmException("Critical issue while processing JVM crash")
        }
    }

    override fun processJavaScriptReport(
        errorName: String,
        message: String,
        stack: String,
        isFatalIssue: Boolean,
        engine: String,
        debugId: String,
        sdkVersion: String,
    ) {
        // no-op for now
    }

    override fun processStrictModeViolation(violation: Violation) {
        // no-op for now
    }

    private class ProcessJvmException(
        message: String,
    ) : Exception(message)
}
