// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import com.google.gson.annotations.SerializedName

/**
 * Represents the model where will store Crash/ANR/etc
 */
internal data class FatalIssueReport(
    val sdk: Sdk,
    @SerializedName("app_metrics")
    val appMetrics: AppMetrics,
    @SerializedName("device_metrics")
    val deviceMetrics: DeviceMetrics,
    val errors: List<ErrorDetails>,
    @SerializedName("thread_details")
    val threadsDetails: ThreadDetails,
)

internal data class Sdk(
    // e.g. io.bitdrift.capture-android"
    val id: String,
    // e.g. 0.17.2 TODO(FranAguilera): BIT-5141. Extract sdk version
    val version: String = "",
)

internal data class AppMetrics(
    @SerializedName("app_id")
    val appId: String,
    val version: String,
    @SerializedName("build_number")
    val buildNumber: BuildNumber,
)

internal data class DeviceMetrics(
    val platform: String = "android",
)

internal data class BuildNumber(
    @SerializedName("version_code")
    val versionCode: Long,
)

internal data class ErrorDetails(
    val name: String,
    val reason: String,
    @SerializedName("stack_trace")
    val stackTrace: List<FrameDetails>,
)

internal data class ThreadEntry(
    // e.g. "main"
    val name: String,
    val active: Boolean,
    val index: Int,
    // e.g. RUNNABLE/WAITING/etc
    val state: String,
    @SerializedName("stack_trace")
    val stackTrace: List<FrameDetails>,
)

internal data class ThreadDetails(
    val count: Int = 0,
    val threads: List<ThreadEntry> = emptyList(),
)

internal data class SourceFile(
    val path: String,
    val lineNumber: Int,
    val column: Int = 0,
)

internal data class FrameDetails(
    // Maps to FrameType
    val type: Int,
    @SerializedName("class_name")
    val className: String,
    // the method or function name
    @SerializedName("symbol_name")
    val symbolName: String,
    @SerializedName("source_file")
    val sourceFile: SourceFile,
    @SerializedName("frame_address")
    val frameAddress: Long = 0,
    @SerializedName("symbol_address")
    val symbolAddress: Long = 0,
    // platform-specific context explaining the thread state, if any. Examples:
    // - locked resource causing thread blocked state
    // - awaited thread ID
    val state: String = "",
)

// JVM: An exception from a Java or Dalvik virtual machine
// DWARF: An event from C/C++ (etc) code which can be symbolicated using DWARF
// AndroidNative: An event from C/C++ (etc) code on Android
internal enum class FrameType {
    UNKNOWN,
    JVM,
    DWARF,
    ANDROID_NATIVE,
}
