// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandInvocation
import io.bitdrift.capture.commands.CommandResult
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(value = ParameterizedRobolectricTestRunner::class)
@Config(sdk = [24])
class SettingsRemoveCommandTest(
    private val inputs: Map<String, String>,
    private val containsKey: Boolean,
) {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("com.example.myprefs", 0)
        prefs
            .edit()
            .putString("existing_string", "some text")
            .putBoolean("existing_bool", true)
            .putBoolean("existing_false", false)
            .putLong("existing_long", 683_800L)
            .putFloat("existing_float", 152.633F)
            .putInt("existing_int", 89)
            .putStringSet("existing_string_set", setOf("a", "b", "c"))
            .commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("com.example.myprefs", 0).edit().clear()
    }

    @Test
    fun testRunCommand(): Unit =
        runBlocking {
            val arguments = inputs.mapValues { CommandArgument.Text(it.value) }
            val scope = CommandInvocation("key", "command", "session_id", arguments)

            val prefs = context.getSharedPreferences(inputs["file_name"], 0)
            assertThat(prefs.contains(inputs["key"])).isEqualTo(containsKey)

            assertThat(SettingsRemoveCommand(context).execute(scope)).isEqualTo(CommandResult.Success())

            assertThat(prefs.contains(inputs["key"])).isFalse
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: remove with {0}")
        fun params(): Iterable<Array<Any>> =
            arrayListOf(
                arrayOf(
                    mapOf(
                        "key" to "existing_string",
                        "file_name" to "com.example.nonexistent",
                    ),
                    false,
                ),
                arrayOf(
                    mapOf(
                        "key" to "unknown_key",
                        "file_name" to "com.example.myprefs",
                    ),
                    false,
                ),
                arrayOf(
                    mapOf(
                        "key" to "existing_string",
                        "file_name" to "com.example.myprefs",
                    ),
                    true,
                ),
                arrayOf(
                    mapOf(
                        "key" to "existing_int",
                        "file_name" to "com.example.myprefs",
                    ),
                    true,
                ),
                arrayOf(
                    mapOf(
                        "key" to "existing_float",
                        "file_name" to "com.example.myprefs",
                    ),
                    true,
                ),
                arrayOf(
                    mapOf(
                        "key" to "existing_bool",
                        "file_name" to "com.example.myprefs",
                    ),
                    true,
                ),
                arrayOf(
                    mapOf(
                        "key" to "existing_false",
                        "file_name" to "com.example.myprefs",
                    ),
                    true,
                ),
                arrayOf(
                    mapOf(
                        "key" to "existing_long",
                        "file_name" to "com.example.myprefs",
                    ),
                    true,
                ),
                arrayOf(
                    mapOf(
                        "key" to "existing_string_set",
                        "file_name" to "com.example.myprefs",
                    ),
                    true,
                ),
            )
    }
}
