// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.reports.AppMetrics
import io.bitdrift.capture.reports.BuildNumber
import io.bitdrift.capture.reports.DeviceMetrics
import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.FatalIssueType
import io.bitdrift.capture.reports.Sdk
import io.bitdrift.capture.reports.ThreadDetails
import io.bitdrift.capture.reports.persistence.IFatalIssueReporterStorage
import java.io.InputStream

/**
 * Process reports from [BUILT_IN] mechanism into a [FatalIssueReport] format
 */
internal class FatalIssueReporterProcessor(
    appContext: Context,
    private val fatalIssueReporterStorage: IFatalIssueReporterStorage,
) {
    private val clientAttributes by lazy {
        // TODO(FranAguilera): BIT-5148 Refactor to avoid recreating ClientAttributes
        ClientAttributes(appContext, ProcessLifecycleOwner.get())
    }
    private val appMetrics: AppMetrics by lazy {
        AppMetrics(
            appId = clientAttributes.appId,
            version = clientAttributes.appVersion,
            buildNumber = BuildNumber(clientAttributes.appVersionCode),
        )
    }
    private val sdk by lazy {
        Sdk(
            id = ClientAttributes.SDK_LIBRARY_ID,
        )
    }
    private val deviceMetrics by lazy { DeviceMetrics() }

    /**
     * Process AppTerminations due to [REASON_ANR] or [REASON_CRASH_NATIVE] into [FatalIssueReport] format
     */
    fun persistAppExitReport(
        fatalIssueType: FatalIssueType,
        timestamp: Long,
        description: String? = null,
        traceInputStream: InputStream,
    ) {
        val report: FatalIssueReport? =
            when (fatalIssueType) {
                FatalIssueType.ANR -> {
                    AppExitAnrTraceProcessor.process(
                        sdk,
                        appMetrics,
                        deviceMetrics,
                        description,
                        traceInputStream,
                    )
                }

                FatalIssueType.NATIVE_CRASH -> {
                    // TODO(FranAguilera): BIT-5144 Handle model for native crash
                    FatalIssueReport(
                        sdk,
                        appMetrics,
                        deviceMetrics,
                        errors = emptyList(),
                        threadsDetails = ThreadDetails(),
                    )
                }

                else -> null
            }

        report?.let {
            fatalIssueReporterStorage
                .persistFatalIssue(
                    timestamp,
                    fatalIssueType,
                    report,
                )
        }
    }

    /**
     * Process JVM crashes into a [FatalIssueReport] format
     *
     * NOTE: This will need to run by default on the caller thread
     */
    @Suppress("UNUSED_PARAMETER")
    fun persistJvmCrash(
        timestamp: Long,
        callerThread: Thread,
        throwable: Throwable,
    ) {
        val fatalIssueReport =
            JvmCrashProcessor.getJvmCrashReport(
                sdk = sdk,
                appMetrics = appMetrics,
                deviceMetrics = deviceMetrics,
                throwable = throwable,
            )
        fatalIssueReporterStorage.persistFatalIssue(
            timestamp,
            FatalIssueType.JVM_CRASH,
            fatalIssueReport,
        )
    }

    internal companion object {
        internal const val UNKNOWN_FIELD_VALUE = "Unknown"
    }
}
