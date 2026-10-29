// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * Registers the SDK's built-in commands with the core and attaches the registry to a logger.
 *
 * @param commands the catalog: every built-in this SDK ships, decided where the SDK is composed.
 */
internal class BuiltinCommandsInstaller(
    private val registry: CommandRegistry,
    private val commands: List<IBuiltinCommand>,
) {
    /**
     * Registers the catalog and attaches [registry] to the logger [loggerId]. Returns the keys of
     * the built-ins the core refused, sorted; the SDK keeps starting without them.
     */
    fun install(loggerId: Long): List<String> = registry.attach(loggerId, commands).sorted()
}
