// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import android.util.Log
import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.BuildConstants
import io.bitdrift.capture.Capture.LOG_TAG
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.AppBuildNumber
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Architecture
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.DeviceMetrics
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.DeviceMetrics.Companion.createCpuAbisVector
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.OSBuild
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Platform
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.SDKInfo
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Timestamp
import io.bitdrift.capture.reports.persistence.IIssueReporterStorage
import java.io.InputStream
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Process reports into a packed format
 */
internal class IssueReporterProcessor(
    private val reporterIssueStorage: IIssueReporterStorage,
    private val clientAttributes: IClientAttributes,
    private val streamingReportsProcessor: IStreamingReportProcessor,
) {
    companion object {
        // Initial size for file builder buffer
        private const val FBS_BUILDER_DEFAULT_SIZE = 1024
    }

    /**
     * Persists a JavaScript report
     *  @param errorName The main readable error name
     *  @param message The detailed JavaScript error message
     *  @param stack Raw stacktrace
     *  @param isFatalIssue Indicates if this is a fatal JSError issue
     *  @param engine Engine type (e.g. hermes/JSC)
     *  @param debuggerId Debugger id that will be used for de-minification
     */
    fun persistJavaScriptReport(
        errorName: String,
        message: String,
        stack: String,
        isFatalIssue: Boolean,
        engine: String,
        debuggerId: String,
    ) {
        // TODO(Fran): To be implemented in follow up PRs
        Log.d(
            LOG_TAG,
            "Persist JS error with name:$errorName, message:$message, isFatalIssue:$isFatalIssue," +
                " engine:$engine, debuggerId:$debuggerId, stack:$stack",
        )
    }

    /**
     * Process AppTerminations due to ANRs and native crashes into packed format
     * @param fatalIssueType The flatbuffer type of fatal issue being processed
     * (e.g. [ReportType.AppNotResponding] or [ReportType.NativeCrash])
     * @param enableNativeCrashReporting Flag indicating if native crash reporting is enabled.
     * Note: This is a temporary flag which may be deleted in the future.
     * @param timestamp The timestamp when the issue occurred
     * @param description Optional description of the issue
     * @param traceInputStream Input stream containing the fatal issue trace data
     */
    fun persistAppExitReport(
        fatalIssueType: Byte,
        enableNativeCrashReporting: Boolean,
        timestamp: Long,
        description: String? = null,
        traceInputStream: InputStream,
    ) {
        if (fatalIssueType == ReportType.AppNotResponding) {
            streamingReportsProcessor.persistANR(
                traceInputStream,
                timestamp,
                reporterIssueStorage.generateFatalIssueFilePath(),
                clientAttributes,
            )
        } else if (fatalIssueType == ReportType.NativeCrash && enableNativeCrashReporting) {
            val builder = FlatBufferBuilder(FBS_BUILDER_DEFAULT_SIZE)
            val sdk = createSDKInfo(builder)
            val appMetrics = createAppMetrics(builder)
            val deviceMetrics = createDeviceMetrics(builder, timestamp)
            val report =
                NativeCrashProcessor.process(
                    builder,
                    sdk,
                    appMetrics,
                    deviceMetrics,
                    description,
                    traceInputStream,
                )
            builder.finish(report)

            reporterIssueStorage.persistFatalIssue(
                timestamp,
                builder.sizedByteArray(),
                ReportType.NativeCrash,
            )
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
        val builder = FlatBufferBuilder(FBS_BUILDER_DEFAULT_SIZE)
        val sdk = createSDKInfo(builder)
        val appMetrics = createAppMetrics(builder)
        val deviceMetrics = createDeviceMetrics(builder, timestamp)
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
        builder.finish(report)

        reporterIssueStorage.persistFatalIssue(
            timestamp,
            builder.sizedByteArray(),
            ReportType.JVMCrash,
        )
    }

    private fun createSDKInfo(builder: FlatBufferBuilder): Int =
        SDKInfo.createSDKInfo(
            builder,
            builder.createString(ClientAttributes.SDK_LIBRARY_ID),
            builder.createString(BuildConstants.SDK_VERSION),
        )

    private fun createAppMetrics(builder: FlatBufferBuilder): Int {
        val buildNumber =
            AppBuildNumber.createAppBuildNumber(builder, clientAttributes.appVersionCode, 0)
        val appId = builder.createString(clientAttributes.appId)
        val appVersion = builder.createString(clientAttributes.appVersion)
        io.bitdrift.capture.reports.binformat.v1.issue_reporting.AppMetrics
            .startAppMetrics(builder)
        io.bitdrift.capture.reports.binformat.v1.issue_reporting.AppMetrics
            .addAppId(builder, appId)
        io.bitdrift.capture.reports.binformat.v1.issue_reporting.AppMetrics
            .addVersion(builder, appVersion)
        io.bitdrift.capture.reports.binformat.v1.issue_reporting.AppMetrics
            .addBuildNumber(builder, buildNumber)
        return io.bitdrift.capture.reports.binformat.v1.issue_reporting.AppMetrics
            .endAppMetrics(builder)
    }

    private fun createDeviceMetrics(
        builder: FlatBufferBuilder,
        timestampMillis: Long,
    ): Int {
        val duration = timestampMillis.toDuration(DurationUnit.MILLISECONDS)
        val cpuAbis =
            createCpuAbisVector(
                builder,
                clientAttributes.supportedAbis.map { builder.createString(it) }.toIntArray(),
            )
        val osBuildVersion = getOsBuildVersion(builder)

        DeviceMetrics.startDeviceMetrics(builder)
        DeviceMetrics.addOsBuild(builder, osBuildVersion)
        DeviceMetrics.addPlatform(builder, Platform.Android)
        DeviceMetrics.addArch(builder, architectureAsFbs(clientAttributes.architecture))
        DeviceMetrics.addCpuAbis(
            builder,
            cpuAbis,
        )
        DeviceMetrics.addTime(
            builder,
            duration.toComponents { seconds, nanoseconds ->
                Timestamp.createTimestamp(builder, seconds.toULong(), nanoseconds.toUInt())
            },
        )
        return DeviceMetrics.endDeviceMetrics(builder)
    }

    private fun getOsBuildVersion(flatBufferBuilder: FlatBufferBuilder): Int {
        val osVersionOffset = flatBufferBuilder.createString(clientAttributes.osVersion)
        OSBuild.startOSBuild(flatBufferBuilder)
        OSBuild.addVersion(flatBufferBuilder, osVersionOffset)
        return OSBuild.endOSBuild(flatBufferBuilder)
    }

    private fun architectureAsFbs(architecture: String): Byte =
        when (architecture.lowercase()) {
            "armeabi", "armeabi-v7a" -> Architecture.arm32
            "arm64-v8a" -> Architecture.arm64
            "x86" -> Architecture.x86
            "x86_64" -> Architecture.x86_64
            else -> Architecture.Unknown
        }
}
