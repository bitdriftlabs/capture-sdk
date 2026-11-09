// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * A command the SDK registers for itself under a well-known numeric type. Each built-in owns its
 * own argument validation and execution; [BuiltinCommandsInstaller] turns the catalog into
 * registrations with the core.
 */
internal interface IBuiltinCommand {
    /** The wire id and key generated from workflow_command.proto. */
    val type: BuiltinCommandType

    /** The artifact contract type ID of the attachment this command returns. */
    val artifactType: String

    /** The core's execution deadline, or null to inherit its runtime-configured default. */
    val timeoutMs: Long?

    suspend fun execute(scope: CommandScope): CommandResult
}
