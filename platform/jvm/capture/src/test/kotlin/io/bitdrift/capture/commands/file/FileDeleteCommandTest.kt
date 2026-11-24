// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.file

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandInvocation
import io.bitdrift.capture.commands.CommandResult
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(value = RobolectricTestRunner::class)
@Config(sdk = [24])
class FileDeleteCommandTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.walkBottomUp().forEach { it.delete() }
    }

    @Test
    fun `delete a nonexistent file`(): Unit =
        runBlocking {
            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf("path" to CommandArgument.Text("fake")),
                )
            assertThat(FileDeleteCommand(context).execute(scope)).isEqualTo(
                CommandResult.Error("file does not exist"),
            )
        }

    @Test
    fun `delete a directory without recursive`(): Unit =
        runBlocking {
            val dir = File("${context.cacheDir}/x")
            assertThat(dir.mkdirs()).isTrue
            assertThat(File("${context.cacheDir}/x/a").createNewFile()).isTrue

            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf("path" to CommandArgument.Text("x")),
                )
            assertThat(FileDeleteCommand(context).execute(scope)).isEqualTo(
                CommandResult.Error(
                    "did not delete file: declining to delete directory without recursive set to true",
                ),
            )
            assertThat(dir.exists()).isTrue
        }

    @Test
    fun `delete a file`(): Unit =
        runBlocking {
            val dir = File("${context.cacheDir}/x")
            val file = File("${context.cacheDir}/x/a")
            assertThat(dir.mkdirs()).isTrue
            assertThat(file.createNewFile()).isTrue

            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf(
                        "path" to CommandArgument.Text("x/a"),
                    ),
                )
            assertThat(FileDeleteCommand(context).execute(scope)).isEqualTo(CommandResult.Success())
            assertThat(dir.exists()).isTrue
            assertThat(file.exists()).isFalse
        }

    @Test
    fun `delete a directory`(): Unit =
        runBlocking {
            val dir = File("${context.cacheDir}/x")
            assertThat(dir.mkdirs()).isTrue
            assertThat(File("${context.cacheDir}/x/a").createNewFile()).isTrue

            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf(
                        "path" to CommandArgument.Text("x"),
                        "recursive" to CommandArgument.Bool(true),
                    ),
                )
            assertThat(FileDeleteCommand(context).execute(scope)).isEqualTo(CommandResult.Success())
            assertThat(dir.exists()).isFalse
        }
}
