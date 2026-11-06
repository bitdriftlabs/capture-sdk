// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

// Generated from workflow_command.proto; do not edit.
package io.bitdrift.capture.commands

enum class BuiltinCommandType(val wireValue: Int, val key: String) {
    DUMP_DEVICE_BUFFER(1, "dump_device_buffer"),
    TAKE_SCREENSHOT(2, "take_screenshot"),
    STACK_SAMPLING_TRACE(3, "stack_sampling_trace"),
}
