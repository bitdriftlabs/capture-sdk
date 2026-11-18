// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.settings

import android.content.Context
import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandArgumentTypeException
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.MissingCommandArgumentException

internal class SettingsSetCommand(
    context: Context,
) : SettingsCommand(context) {
    override val type: BuiltinCommandType = BuiltinCommandType.USER_SETTINGS_SET

    override suspend fun execute(scope: CommandScope): CommandResult {
        val key = scope.argument("key")
        val value = scope.arguments["value"] ?: throw MissingCommandArgumentException("value")
        with(getSharedPreferences(scope).edit()) {
            when (value) {
                is CommandArgument.Binary -> throw invalidValue(value)
                is CommandArgument.UInt64 -> throw invalidValue(value)
                is CommandArgument.Bool -> putBoolean(key, value.value)
                is CommandArgument.Float64 -> putFloat(key, value.value.toFloat())
                is CommandArgument.Int64 -> putLong(key, value.value)
                is CommandArgument.Text -> putString(key, value.value)
            }
            apply()
        }
        return CommandResult.Success()
    }

    private fun invalidValue(value: CommandArgument): CommandArgumentTypeException =
        CommandArgumentTypeException("value", "bool, (signed) integer, float, or string", value)
}
