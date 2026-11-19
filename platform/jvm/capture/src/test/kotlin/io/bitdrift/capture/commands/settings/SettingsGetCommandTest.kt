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
class SettingsGetCommandTest(
    private val arguments: Map<String, CommandArgument>,
    private val result: CommandResult,
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
            .putLong("existing_long", 683_800)
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
            val scope = CommandInvocation("key", "command", "session_id", arguments)
            assertThat(SettingsGetCommand(context).execute(scope)).isEqualTo(result)
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: get with {0} should return {1}")
        fun params(): Iterable<Array<Any>> =
            arrayListOf(
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("existing_string"),
                        "file_name" to CommandArgument.Text("com.example.nonexistent"),
                    ),
                    CommandResult.Error("unable to fetch value: key 'existing_string' is not set"),
                ),
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("unknown_key"),
                        "file_name" to CommandArgument.Text("com.example.myprefs"),
                    ),
                    CommandResult.Error("unable to fetch value: key 'unknown_key' is not set"),
                ),
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("existing_string"),
                        "file_name" to CommandArgument.Text("com.example.myprefs"),
                    ),
                    CommandResult.Success(context = mapOf("existing_string" to "some text")),
                ),
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("existing_int"),
                        "file_name" to CommandArgument.Text("com.example.myprefs"),
                    ),
                    CommandResult.Success(context = mapOf("existing_int" to "89")),
                ),
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("existing_float"),
                        "file_name" to CommandArgument.Text("com.example.myprefs"),
                    ),
                    CommandResult.Success(context = mapOf("existing_float" to "152.633")),
                ),
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("existing_bool"),
                        "file_name" to CommandArgument.Text("com.example.myprefs"),
                    ),
                    CommandResult.Success(context = mapOf("existing_bool" to "true")),
                ),
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("existing_false"),
                        "file_name" to CommandArgument.Text("com.example.myprefs"),
                    ),
                    CommandResult.Success(context = mapOf("existing_false" to "false")),
                ),
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("existing_long"),
                        "file_name" to CommandArgument.Text("com.example.myprefs"),
                    ),
                    CommandResult.Success(context = mapOf("existing_long" to "683800")),
                ),
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("existing_string_set"),
                        "file_name" to CommandArgument.Text("com.example.myprefs"),
                    ),
                    CommandResult.Success(context = mapOf("existing_string_set" to "[a, b, c]")),
                ),
            )
    }
}
