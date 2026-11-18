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
    STACK_SAMPLING_TRACE(3, "stack_sampling_trace"),
    USER_SETTINGS_REMOVE(4, "user_settings_remove"),
    USER_SETTINGS_SET(5, "user_settings_set"),
    USER_SETTINGS_GET(6, "user_settings_get"),
    USER_SETTINGS_CLEAR(7, "user_settings_clear"),
    FILE_LIST(8, "file_list"),
    FILE_SIZE(9, "file_size"),
    FILE_CLEAR(10, "file_clear"),
    FILE_DELETE(11, "file_delete"),
    MEMORY_DUMP(12, "memory_dump"),
}
