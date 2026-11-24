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
class FileSizeCommandTest {
    private lateinit var context: Context
    private val kb = 1_024

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.walkBottomUp().forEach { it.delete() }
    }

    @Test
    fun `size of nonexistent file`(): Unit =
        runBlocking {
            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf("path" to CommandArgument.Text("fake")),
                )
            assertThat(FileSizeCommand(context).execute(scope)).isEqualTo(
                CommandResult.Error("file does not exist"),
            )
        }

    @Test
    fun `size of empty cache`(): Unit =
        runBlocking {
            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf(),
                )
            assertThat(FileSizeCommand(context).execute(scope)).isEqualTo(
                CommandResult.Success(context = mapOf("file_size_kb" to "0")),
            )
        }

    @Test
    fun `size of file`(): Unit =
        runBlocking {
            val dir = File("${context.cacheDir}/x")
            val file1 = File("${context.cacheDir}/x/a")
            val file2 = File("${context.cacheDir}/x/b")
            assertThat(dir.mkdirs()).isTrue
            assertThat(file1.createNewFile()).isTrue
            assertThat(file2.createNewFile()).isTrue
            file1.writeText("a".repeat(kb * 2))
            file2.writeText("b".repeat(kb * 4))

            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf("path" to CommandArgument.Text("x/b")),
                )
            assertThat(FileSizeCommand(context).execute(scope)).isEqualTo(
                CommandResult.Success(context = mapOf("file_size_kb" to "4")),
            )
        }

    @Test
    fun `size of subdirectory`(): Unit =
        runBlocking {
            val file1 = File("${context.cacheDir}/x/a")
            val file2 = File("${context.cacheDir}/x/b")
            val subdir = File("${context.cacheDir}/x/c")
            val file3 = File("${context.cacheDir}/x/c/1")
            val dir = File("${context.cacheDir}/y")
            val file4 = File("${context.cacheDir}/y/d")

            assertThat(dir.mkdirs()).isTrue
            assertThat(subdir.mkdirs()).isTrue
            assertThat(file1.createNewFile()).isTrue
            assertThat(file2.createNewFile()).isTrue
            assertThat(file3.createNewFile()).isTrue
            assertThat(file4.createNewFile()).isTrue
            file1.writeText("a".repeat(kb * 2))
            file2.writeText("b".repeat(kb * 4))
            file3.writeText("c".repeat(kb * 5))
            file4.writeText("d".repeat(kb * 1))

            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf("path" to CommandArgument.Text("x")),
                )
            assertThat(FileSizeCommand(context).execute(scope)).isEqualTo(
                CommandResult.Success(context = mapOf("file_size_kb" to "11")),
            )
        }

    @Test
    fun `size of cache`(): Unit =
        runBlocking {
            val file1 = File("${context.cacheDir}/x/a")
            val file2 = File("${context.cacheDir}/x/b")
            val subdir = File("${context.cacheDir}/x/c")
            val file3 = File("${context.cacheDir}/x/c/1")
            val dir = File("${context.cacheDir}/y")
            val file4 = File("${context.cacheDir}/y/d")

            assertThat(dir.mkdirs()).isTrue
            assertThat(subdir.mkdirs()).isTrue
            assertThat(file1.createNewFile()).isTrue
            assertThat(file2.createNewFile()).isTrue
            assertThat(file3.createNewFile()).isTrue
            assertThat(file4.createNewFile()).isTrue
            file1.writeText("a".repeat(kb * 2))
            file2.writeText("b".repeat(kb * 4))
            file3.writeText("c".repeat(kb * 5))
            file4.writeText("d".repeat(kb * 1))

            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf(),
                )
            assertThat(FileSizeCommand(context).execute(scope)).isEqualTo(
                CommandResult.Success(context = mapOf("file_size_kb" to "12")),
            )
        }
}
