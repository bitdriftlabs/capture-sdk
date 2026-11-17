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
import io.bitdrift.capture.commands.CommandInvocation
import io.bitdrift.capture.commands.MissingCommandArgumentException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(value = ParameterizedRobolectricTestRunner::class)
@Config(sdk = [24])
class SettingsGetCommandInvalidArgsTest(
    private val arguments: Map<String, CommandArgument>,
    private val exceptionClass: Class<Throwable>,
    private val exceptionMessage: String,
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
    fun testRunCommand() {
        val throwable =
            assertThrows(exceptionClass) {
                runBlocking {
                    val scope = CommandInvocation("key", "command", "session_id", arguments)
                    SettingsGetCommand(context).execute(scope)
                }
            }
        assertThat(throwable.message).isEqualTo(exceptionMessage)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: get with {0} should return exception {1}")
        fun params(): Iterable<Array<Any>> =
            arrayListOf(
                arrayOf(
                    mapOf(
                        "key" to CommandArgument.Text("existing_string"),
                    ),
                    MissingCommandArgumentException::class.java,
                    "missing argument file_name",
                ),
                arrayOf(
                    mapOf(
                        "file_name" to CommandArgument.Text("com.example.myprefs"),
                    ),
                    MissingCommandArgumentException::class.java,
                    "missing argument key",
                ),
                arrayOf(
                    mapOf<String, CommandArgument>(),
                    MissingCommandArgumentException::class.java,
                    "missing argument key",
                ),
            )
    }
}
