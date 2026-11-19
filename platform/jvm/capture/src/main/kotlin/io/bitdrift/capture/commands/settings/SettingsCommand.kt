// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.settings

import android.content.Context
import android.content.SharedPreferences
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.IBuiltinCommand

internal abstract class SettingsCommand(
    private val context: Context,
) : IBuiltinCommand {
    override val artifactType: String = "settings"
    override val timeoutMs: Long? = 100

    fun getSharedPreferences(scope: CommandScope): SharedPreferences =
        scope.argument("file_name").let { context.getSharedPreferences(it, 0) }
}
