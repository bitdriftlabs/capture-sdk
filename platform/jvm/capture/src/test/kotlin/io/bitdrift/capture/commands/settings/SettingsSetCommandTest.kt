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
class SettingsSetCommandTest(
    private val key: String,
    private val value: CommandArgument,
    private val defaultValue: Any,
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
        context.getSharedPreferences("com.example.newprefs", 0).edit().clear()
    }

    @Test
    fun testSetValueInExistingPrefs(): Unit =
        runBlocking {
            val arguments =
                mapOf(
                    "key" to CommandArgument.Text(key),
                    "file_name" to CommandArgument.Text("com.example.myprefs"),
                    "value" to value,
                )
            val scope = CommandInvocation("key", "command", "session_id", arguments)

            assertThat(SettingsSetCommand(context).execute(scope)).isEqualTo(CommandResult.Success())
            assertThat(prefs.get(key, defaultValue)).isEqualTo(value.value())
        }

    @Test
    fun testOverwriteValue(): Unit =
        runBlocking {
            val key = "existing_$key"
            val arguments =
                mapOf(
                    "key" to CommandArgument.Text(key),
                    "file_name" to CommandArgument.Text("com.example.myprefs"),
                    "value" to value,
                )
            val scope = CommandInvocation("key", "command", "session_id", arguments)

            assertThat(SettingsSetCommand(context).execute(scope)).isEqualTo(CommandResult.Success())
            assertThat(prefs.get(key, defaultValue)).isEqualTo(value.value())
        }

    @Test
    fun testSetValueInNonexistentPrefs(): Unit =
        runBlocking {
            val arguments =
                mapOf(
                    "key" to CommandArgument.Text(key),
                    "file_name" to CommandArgument.Text("com.example.newprefs"),
                    "value" to value,
                )
            val scope = CommandInvocation("key", "command", "session_id", arguments)

            assertThat(SettingsSetCommand(context).execute(scope)).isEqualTo(CommandResult.Success())
            val prefs = context.getSharedPreferences("com.example.newprefs", 0)
            assertThat(prefs.get(key, defaultValue)).isEqualTo(value.value())
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: set {0} to {1}")
        fun params(): Iterable<Array<Any>> =
            arrayListOf(
                arrayOf<Any>("string", CommandArgument.Text("hello!"), ""),
                arrayOf<Any>("false", CommandArgument.Bool(false), true),
                arrayOf<Any>("true", CommandArgument.Bool(true), false),
                arrayOf<Any>("long", CommandArgument.Int64(3_237_986L), 0L),
                arrayOf<Any>("float", CommandArgument.Float64(0.8), 0.0F),
            )
    }
}

private inline fun <reified T> SharedPreferences.get(
    key: String,
    defaultValue: T,
) =
    @Suppress("UNCHECKED_CAST")
    when (defaultValue) {
        is Boolean -> getBoolean(key, defaultValue) as T
        is Float -> getFloat(key, defaultValue) as T
        is Int -> getInt(key, defaultValue) as T
        is Long -> getLong(key, defaultValue) as T
        is Set<*> -> getStringSet(key, defaultValue as Set<String>) as T
        is String -> getString(key, defaultValue) as T
        else -> throw UnsupportedOperationException("unexpected class ${T::class} used for value $defaultValue")
    }

private fun CommandArgument.value(): Any =
    when (this) {
        is CommandArgument.Binary -> this.value
        is CommandArgument.Bool -> this.value
        is CommandArgument.Float64 -> this.value.toFloat()
        is CommandArgument.Int64 -> this.value
        is CommandArgument.Text -> this.value
        is CommandArgument.UInt64 -> this.value.toLong()
    }
