// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import android.os.Build
import android.os.strictmode.Violation
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.BuildConstants
import io.bitdrift.capture.Capture.LOG_TAG
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.AppBuildNumber
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Architecture
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.DeviceMetrics
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.DeviceMetrics.Companion.createCpuAbisVector
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.OSBuild
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Platform
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.SDKInfo
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Timestamp
import io.bitdrift.capture.reports.persistence.IIssueReporterStore
import java.io.InputStream
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Process reports into a packed format
 */
internal class IssueReporterProcessor(
    private val reporterIssueStore: IIssueReporterStore,
    private val clientAttributes: IClientAttributes,
    private val streamingReportsProcessor: IStreamingReportProcessor,
    private val dateProvider: DateProvider,
) : IIssueReporterProcessor {
    companion object {
        // Initial size for file builder buffer
        private const val FBS_BUILDER_DEFAULT_SIZE = 1024
    }

    /**
     * Processes and persists a JavaScript report.
     *  @param errorName The main readable error name
     *  @param message The detailed JavaScript error message
     *  @param stack Raw stacktrace
     *  @param isFatalIssue Indicates if this is a fatal JSError issue
     *  @param engine Engine type (e.g. hermes/JSC)
     * @param debugId Debug id that will be used for de-minification
     * @param sdkVersion bitdrift's React Native SDK version(e.g 8.1)
     */
    override fun processJavaScriptReport(
        errorName: String,
        message: String,
        stack: String,
        isFatalIssue: Boolean,
        engine: String,
        debugId: String,
        sdkVersion: String,
    ) {
        runCatching {
            val timestamp = dateProvider.invoke().time
            val destinationPath =
                if (isFatalIssue) {
                    reporterIssueStore.generateFatalIssueFilePath()
                } else {
                    reporterIssueStore.generateNonFatalIssueFilePath()
                }

            streamingReportsProcessor.processAndPersistJavaScriptError(
                errorName = errorName,
                errorMessage = message,
                stackTrace = stack,
                isFatal = isFatalIssue,
                engine = engine,
                debugId = debugId,
                timestampMillis = timestamp,
                destinationPath = destinationPath,
                attributes = clientAttributes,
                sdkVersion = sdkVersion,
            )
        }.onFailure {
            Log.e(LOG_TAG, "Error at persistJavaScriptReport: $it", it)
        }
    }

    /**
     * Processes AppTerminations due to ANRs and native crashes into packed format.
     * @param fatalIssueType The flatbuffer type of fatal issue being processed
     * (e.g. [ReportType.AppNotResponding] or [ReportType.NativeCrash])
     * @param timestamp The timestamp when the issue occurred
     * @param description Optional description of the issue
     * @param traceInputStream Input stream containing the fatal issue trace data
     */
    override fun processAppExitReport(
        fatalIssueType: Byte,
        timestamp: Long,
        description: String?,
        traceInputStream: InputStream,
    ) {
        if (fatalIssueType == ReportType.AppNotResponding) {
            streamingReportsProcessor.processAndPersistANR(
                traceInputStream,
                timestamp,
                reporterIssueStore.generateFatalIssueFilePath(),
                clientAttributes,
            )
        } else if (fatalIssueType == ReportType.NativeCrash) {
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

            reporterIssueStore.persistFatalIssue(
                timestamp,
                builder.sizedByteArray(),
                ReportType.NativeCrash,
            )
        }
    }

    /**
     * Processes JVM crashes into a packed format.
     *
     * NOTE: This will need to run by default on the caller thread
     */
    override fun processJvmCrash(
        callerThread: Thread,
        throwable: Throwable,
        allThreads: Map<Thread, Array<StackTraceElement>>?,
    ) {
        processAndPersistJvmIssue(
            callerThread = callerThread,
            throwable = throwable,
            allThreads = allThreads,
            reportType = ReportType.JVMCrash,
            isFatal = true,
        )
    }

    /**
     * Processes StrictMode violations into a packed format.
     *
     * NOTE: This will need to run by default on the caller thread
     */
    @RequiresApi(Build.VERSION_CODES.P)
    override fun processStrictModeViolation(violation: Violation) {
        processAndPersistJvmIssue(
            callerThread = Thread.currentThread(),
            throwable = violation,
            allThreads = Thread.getAllStackTraces(),
            reportType = ReportType.StrictModeViolation,
            isFatal = false,
        )
    }

    private fun processAndPersistJvmIssue(
        callerThread: Thread,
        throwable: Throwable,
        allThreads: Map<Thread, Array<StackTraceElement>>?,
        reportType: Byte,
        isFatal: Boolean,
    ) {
        val timestamp = dateProvider.invoke().time
        val builder = FlatBufferBuilder(FBS_BUILDER_DEFAULT_SIZE)
        val sdk = createSDKInfo(builder)
        val appMetrics = createAppMetrics(builder)
        val deviceMetrics = createDeviceMetrics(builder, timestamp)
        val report =
            JvmProcessor.getJvmReport(
                builder,
                sdk,
                appMetrics,
                deviceMetrics,
                throwable,
                callerThread,
                allThreads,
                reportType,
            )
        builder.finish(report)

        if (isFatal) {
            reporterIssueStore.persistFatalIssue(
                timestamp,
                builder.sizedByteArray(),
                reportType,
            )
        } else {
            reporterIssueStore.persistNonFatalIssue(
                timestamp,
                builder.sizedByteArray(),
                reportType,
            )
        }
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
