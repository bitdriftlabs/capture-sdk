// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType

/** Registers the SDK's built-in commands with the core and attaches the registry to a logger. */
internal class BuiltinCommandsInstaller(
    private val registry: CommandRegistry,
    private val logger: IInternalLogger,
) {
    /**
     * Registers [commands] and attaches [registry] to the logger [loggerId]. Returns the keys the
     * core refused, sorted; the SDK keeps starting without them and logs the refusal.
     */
    fun install(
        commands: List<IBuiltinCommand>,
        loggerId: Long,
    ): List<String> {
        commands.forEach { command ->
            registry.registerBuiltin(command.type.wireValue, command.type.key, command.artifactType, command.timeoutMs) {
                command.execute(this)
            }
        }
        val failed = registry.attach(loggerId).sorted()
        if (failed.isNotEmpty()) {
            logger.logInternal(LogType.INTERNALSDK, LogLevel.ERROR) {
                "failed to register SDK built-ins: ${failed.joinToString(",")}"
            }
        }
        return failed
    }
}
