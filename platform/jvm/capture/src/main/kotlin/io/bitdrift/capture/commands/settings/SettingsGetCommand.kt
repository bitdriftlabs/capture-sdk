// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.settings

import android.content.Context
import android.content.SharedPreferences
import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope

internal class SettingsGetCommand(
    context: Context,
) : SettingsCommand(context) {
    override val type: BuiltinCommandType = BuiltinCommandType.USER_SETTINGS_GET

    override suspend fun execute(scope: CommandScope): CommandResult {
        val key = scope.argument("key")
        getSharedPreferences(scope).getStringFromAny(key)?.let {
            return CommandResult.Success(context = mapOf(key to it))
        }
        return CommandResult.Error("unable to fetch value: key '$key' is not set")
    }
}

private fun SharedPreferences.getStringFromAny(preferenceKey: String): String? {
    for (fetch in arrayOf<(String) -> Any?>(
        // placed the nullable check first to always return null for unset values. otherwise
        // cycles through the remaining value types in the case of a `ClassCastException`
        { key -> this.getString(key, null) },
        { key -> this.getBoolean(key, false) },
        { key -> this.getFloat(key, 0.0F) },
        { key -> this.getInt(key, 0) },
        { key -> this.getLong(key, 0) },
        { key -> this.getStringSet(key, setOf()) },
    )) {
        try {
            return fetch(preferenceKey)?.toString()
        } catch (_: ClassCastException) {
            continue
        }
    }
    return null
}
