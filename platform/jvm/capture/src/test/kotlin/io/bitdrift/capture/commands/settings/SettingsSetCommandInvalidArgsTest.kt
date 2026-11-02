// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandArgumentTypeException
import io.bitdrift.capture.commands.CommandInvocation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(value = ParameterizedRobolectricTestRunner::class)
class SettingsSetCommandInvalidArgsTest(
    private val value: CommandArgument,
    private val exceptionClass: Class<Throwable>,
) {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("com.example.myprefs", 0).edit().clear()
    }

    @Test
    fun testBadValues() {
        val arguments =
            mapOf(
                "key" to CommandArgument.Text("something"),
                "file_name" to CommandArgument.Text("com.example.myprefs"),
                "value" to value,
            )
        assertThrows(exceptionClass) {
            runBlocking {
                val scope = CommandInvocation("key", "command", "session_id", arguments)
                SettingsSetCommand(context).execute(scope)
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: set {0} to {1}")
        fun params(): Iterable<Array<Any>> =
            arrayListOf(
                arrayOf<Any>(
                    CommandArgument.Binary("this could be anything!!".encodeToByteArray()),
                    CommandArgumentTypeException::class.java,
                ),
                arrayOf<Any>(
                    CommandArgument.UInt64(304UL),
                    CommandArgumentTypeException::class.java,
                ),
            )
    }
}
