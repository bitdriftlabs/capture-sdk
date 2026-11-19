// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// Generated from workflow_command.proto; do not edit.
package io.bitdrift.capture.commands

enum class BuiltinCommandType(val wireValue: Int, val key: String) {
    DUMP_DEVICE_BUFFER(1, "dump_device_buffer"),
    TAKE_SCREENSHOT(2, "take_screenshot"),
    STACK_SAMPLING_TRACE(3, "stack_sampling_trace") {
        override fun parse(scope: CommandScope) =
            StackSamplingTraceArguments(
                durationMs = scope.argument<Long>("duration_ms"),
                frequencyHz = scope.argument<Long>("frequency_hz"),
                bufferSizeKb = scope.argument<Long>("buffer_size_kb"),
            )
    },
    USER_SETTINGS_REMOVE(4, "user_settings_remove"),
    USER_SETTINGS_SET(5, "user_settings_set"),
    USER_SETTINGS_GET(6, "user_settings_get"),
    USER_SETTINGS_CLEAR(7, "user_settings_clear"),
    FILE_LIST(8, "file_list"),
    FILE_SIZE(9, "file_size"),
    FILE_CLEAR(10, "file_clear"),
    FILE_DELETE(11, "file_delete"),
    MEMORY_DUMP(12, "memory_dump"),
    HEAP_PROFILE_TRACE(13, "heap_profile_trace") {
        override fun parse(scope: CommandScope) =
            HeapProfileTraceArguments(
                durationMs = scope.argument<Long>("duration_ms"),
                bufferSizeKb = scope.argument<Long>("buffer_size_kb"),
                samplingIntervalBytes = scope.argument<Long>("sampling_interval_bytes"),
                trackJavaAllocations = scope.argument<Boolean>("track_java_allocations"),
            )
    },
    SYSTEM_TRACE(14, "system_trace") {
        override fun parse(scope: CommandScope) =
            SystemTraceArguments(
                durationMs = scope.argument<Long>("duration_ms"),
            )
    },
    JAVA_HEAP_DUMP(15, "java_heap_dump") {
        override fun parse(scope: CommandScope) =
            JavaHeapDumpArguments(
                bufferSizeKb = scope.argument<Long>("buffer_size_kb"),
            )
    },
    ;

    /** This command's generated arguments, or null if it does not use a generated parser. */
    internal open fun parse(scope: CommandScope): BuiltinCommandArguments? = null
}

/**
 * The arguments of a built-in that declares any, generated from its proto definition. Open, so a
 * consumer handles only the arguments it serves and a new command never breaks an unrelated one.
 */
internal interface BuiltinCommandArguments

internal data class StackSamplingTraceArguments(
    val durationMs: Long,
    val frequencyHz: Long,
    val bufferSizeKb: Long,
) : BuiltinCommandArguments

internal data class HeapProfileTraceArguments(
    val durationMs: Long,
    val bufferSizeKb: Long,
    val samplingIntervalBytes: Long,
    val trackJavaAllocations: Boolean,
) : BuiltinCommandArguments

internal data class SystemTraceArguments(
    val durationMs: Long,
) : BuiltinCommandArguments

internal data class JavaHeapDumpArguments(
    val bufferSizeKb: Long,
) : BuiltinCommandArguments
