// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.reports.binformat.v1.AppBuildNumber
import io.bitdrift.capture.reports.binformat.v1.Platform
import io.bitdrift.capture.reports.binformat.v1.Report
import io.bitdrift.capture.reports.binformat.v1.ReportType
import io.bitdrift.capture.reports.binformat.v1.SDKInfo
import io.bitdrift.capture.reports.persistence.IFatalIssueReporterStorage
import java.io.InputStream

/**
 * Process reports into a packed format
 */
internal class FatalIssueReporterProcessor(
    appContext: Context,
    private val fatalIssueReporterStorage: IFatalIssueReporterStorage,
) {
    // Initial size for file builder buffer
    private val builderDefaultSize = 1024
    private val clientAttributes by lazy {
        // TODO(FranAguilera): BIT-5148 Refactor to avoid recreating ClientAttributes
        ClientAttributes(appContext, ProcessLifecycleOwner.get())
    }

    /**
     * Process AppTerminations due to ANRs and native crashes into packed format
     */
    fun persistAppExitReport(
        fatalIssueType: Byte,
        timestamp: Long,
        description: String? = null,
        traceInputStream: InputStream,
    ) {
        val builder = FlatBufferBuilder(builderDefaultSize)
        val sdk = createSDKInfo(builder)
        val appMetrics = createAppMetrics(builder)
        val deviceMetrics = createDeviceMetrics(builder)

        val report: Int? =
            when (fatalIssueType) {
                ReportType.AppNotResponding -> {
                    AppExitAnrTraceProcessor.process(
                        builder,
                        clientAttributes.appId,
                        sdk,
                        appMetrics,
                        deviceMetrics,
                        description,
                        traceInputStream,
                    )
                }

                ReportType.NativeCrash -> {
                    // TODO(FranAguilera): BIT-5144 Handle model for native crash
                    Report.createReport(
                        builder,
                        sdk,
                        ReportType.NativeCrash,
                        appMetrics,
                        deviceMetrics,
                        0,
                        0,
                        0,
                    )
                }

                else -> null
            }

        report?.let {
            persistReport(timestamp, builder, report)
        }
    }

    /**
     * Process JVM crashes into a packed format
     *
     * NOTE: This will need to run by default on the caller thread
     */
    fun persistJvmCrash(
        timestamp: Long,
        callerThread: Thread,
        throwable: Throwable,
        allThreads: Map<Thread, Array<StackTraceElement>>?,
    ) {
        val builder = FlatBufferBuilder(builderDefaultSize)
        val sdk = createSDKInfo(builder)
        val appMetrics = createAppMetrics(builder)
        val deviceMetrics = createDeviceMetrics(builder)
        val report =
            JvmCrashProcessor.getJvmCrashReport(
                builder,
                sdk,
                appMetrics,
                deviceMetrics,
                throwable,
                callerThread,
                allThreads,
            )

        persistReport(timestamp, builder, report)
    }

    private fun persistReport(
        timestamp: Long,
        builder: FlatBufferBuilder,
        reportOffset: Int,
    ) {
        builder.finish(reportOffset)
        fatalIssueReporterStorage.persistFatalIssue(timestamp, builder.sizedByteArray())
    }

    private fun createSDKInfo(builder: FlatBufferBuilder): Int =
        SDKInfo.createSDKInfo(
            builder,
            builder.createString(ClientAttributes.SDK_LIBRARY_ID),
            // e.g. 0.17.2 TODO(FranAguilera): BIT-5141. Extract sdk version
            builder.createString(""),
        )

    private fun createAppMetrics(builder: FlatBufferBuilder): Int {
        val buildNumber = AppBuildNumber.createAppBuildNumber(builder, clientAttributes.appVersionCode, 0)
        val appId = builder.createString(clientAttributes.appId)
        val appVersion = builder.createString(clientAttributes.appVersion)
        io.bitdrift.capture.reports.binformat.v1.AppMetrics
            .startAppMetrics(builder)
        io.bitdrift.capture.reports.binformat.v1.AppMetrics
            .addAppId(builder, appId)
        io.bitdrift.capture.reports.binformat.v1.AppMetrics
            .addVersion(builder, appVersion)
        io.bitdrift.capture.reports.binformat.v1.AppMetrics
            .addBuildNumber(builder, buildNumber)
        return io.bitdrift.capture.reports.binformat.v1.AppMetrics
            .endAppMetrics(builder)
    }

    private fun createDeviceMetrics(builder: FlatBufferBuilder): Int {
        // TODO: BIT-5246 Add device info
        io.bitdrift.capture.reports.binformat.v1.DeviceMetrics
            .startDeviceMetrics(builder)
        io.bitdrift.capture.reports.binformat.v1.DeviceMetrics
            .addPlatform(builder, Platform.Android)
        return io.bitdrift.capture.reports.binformat.v1.DeviceMetrics
            .endDeviceMetrics(builder)
    }

    internal companion object {
        internal const val UNKNOWN_FIELD_VALUE = "Unknown"
    }
}
