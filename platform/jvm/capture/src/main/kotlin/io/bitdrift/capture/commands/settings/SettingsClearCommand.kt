// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.settings

import android.content.Context
import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope

internal class SettingsClearCommand(
    context: Context,
) : SettingsCommand(context) {
    override val type: BuiltinCommandType = BuiltinCommandType.USER_SETTINGS_CLEAR

    override suspend fun execute(scope: CommandScope): CommandResult {
        getSharedPreferences(scope).edit().clear().apply()
        return CommandResult.Success()
    }
}
