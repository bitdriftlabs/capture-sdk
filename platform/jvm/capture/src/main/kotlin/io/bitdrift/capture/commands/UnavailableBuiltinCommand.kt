// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * A catalog entry whose capability is absent on this build or device. It keeps the registration
 * with the core, so the backend sees a defined command, and fails every invocation with [title].
 */
internal class UnavailableBuiltinCommand(
    override val type: BuiltinCommandType,
    override val artifactType: String,
    override val timeoutMs: Long?,
    private val title: String,
    private val description: String? = null,
) : IBuiltinCommand {
    override suspend fun execute(scope: CommandScope): CommandResult = scope.error(title, description)
}
