// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.BuildConstants
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.reports.binformat.v1.AppBuildNumber
import io.bitdrift.capture.reports.binformat.v1.Architecture
import io.bitdrift.capture.reports.binformat.v1.DeviceMetrics
import io.bitdrift.capture.reports.binformat.v1.DeviceMetrics.Companion.createCpuAbisVector
import io.bitdrift.capture.reports.binformat.v1.OSBuild
import io.bitdrift.capture.reports.binformat.v1.Platform
import io.bitdrift.capture.reports.binformat.v1.ReportType
import io.bitdrift.capture.reports.binformat.v1.SDKInfo
import io.bitdrift.capture.reports.binformat.v1.Timestamp
import io.bitdrift.capture.reports.persistence.IFatalIssueReporterStorage
import java.io.InputStream
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Process reports into a packed format
 */
internal class FatalIssueReporterProcessor(
    private val fatalIssueReporterStorage: IFatalIssueReporterStorage,
    private val clientAttributes: IClientAttributes,
) {
    /**
     * Process AppTerminations due to ANRs and native crashes into packed format
     */
    fun persistAppExitReport(
        fatalIssueType: Byte,
        timestamp: Long,
        description: String? = null,
        traceInputStream: InputStream,
    ) {
        val builder = FlatBufferBuilder(FBS_BUILDER_DEFAULT_SIZE)
        val sdk = createSDKInfo(builder)
        val appMetrics = createAppMetrics(builder)
        val deviceMetrics = createDeviceMetrics(builder, timestamp)

        val report: Int? =
            when (fatalIssueType) {
                ReportType.AppNotResponding -> {
                    AppExitAnrTraceProcessor.process(
                        builder,
                        sdk,
                        appMetrics,
                        deviceMetrics,
                        description,
                        traceInputStream,
                    )
                }

                ReportType.NativeCrash -> {
                    // TODO(FranAguilera): BIT-5823 use NativeCrashProcessor once async processing is ready
                    null
                }

                else -> null
            }

        report?.let {
            persistReport(timestamp, builder, report, fatalIssueType)
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

        persistReport(timestamp, builder, report, ReportType.JVMCrash)
    }

    private fun persistReport(
        timestamp: Long,
        builder: FlatBufferBuilder,
        reportOffset: Int,
        reportType: Byte,
    ) {
        builder.finish(reportOffset)
        fatalIssueReporterStorage.persistFatalIssue(timestamp, builder.sizedByteArray(), reportType)
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

    private companion object {
        // Initial size for file builder buffer
        private const val FBS_BUILDER_DEFAULT_SIZE = 1024
    }
}
