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
import io.bitdrift.capture.commands.CommandAttachment
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
class FileListCommandTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.walkBottomUp().forEach { it.delete() }
    }

    @Test
    fun `listing an empty cache`(): Unit =
        runBlocking {
            val scope = CommandInvocation("key", "command", "session_id", mapOf())
            assertThat(FileListCommand(context).execute(scope)).isEqualTo(
                CommandResult.Success(
                    context = mapOf("file_count" to "0", "contents" to ""),
                ),
            )
        }

    @Test
    fun `listing a nonexistent directory`(): Unit =
        runBlocking {
            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf("path" to CommandArgument.Text("fake")),
                )
            assertThat(FileListCommand(context).execute(scope)).isEqualTo(
                CommandResult.Error("file does not exist"),
            )
        }

    @Test
    fun `listing an existing non-directory`(): Unit =
        runBlocking {
            assertThat(File("${context.cacheDir}/y").createNewFile()).isTrue
            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf("path" to CommandArgument.Text("y")),
                )
            assertThat(FileListCommand(context).execute(scope)).isEqualTo(
                CommandResult.Error("failed to access path"),
            )
        }

    @Test
    fun `listing files and directories`(): Unit =
        runBlocking {
            val dir = File("${context.cacheDir}/x")
            assertThat(dir.mkdirs()).isTrue
            assertThat(File("${context.cacheDir}/x/a").createNewFile()).isTrue
            assertThat(File("${context.cacheDir}/x/b").createNewFile()).isTrue
            assertThat(File("${context.cacheDir}/y").createNewFile()).isTrue

            val scope = CommandInvocation("key", "command", "session_id", mapOf())
            assertThat(FileListCommand(context).execute(scope)).isEqualTo(
                CommandResult.Success(
                    context = mapOf("file_count" to "2", "contents" to "x\ny"),
                ),
            )
        }

    @Test
    fun `listing a lot of files`(): Unit =
        runBlocking {
            for (i in 1..300) {
                val fileIndex = "$i".padStart(3, '0')
                assertThat(
                    File(
                        "${context.cacheDir}/file$fileIndex.txt",
                    ).createNewFile(),
                ).isTrue
            }

            val scope = CommandInvocation("key", "command", "session_id", mapOf())
            val expectedContents =
                (1..300).joinToString("\n") { "file${"$it".padStart(3, '0')}.txt" }

            assertThat(FileListCommand(context).execute(scope)).isEqualTo(
                CommandResult.Success(
                    CommandAttachment(expectedContents.encodeToByteArray(), "text/plain"),
                    mapOf("file_count" to "300"),
                ),
            )
        }

    @Test
    fun `listing files and directories in subdirectory`(): Unit =
        runBlocking {
            val dir = File("${context.cacheDir}/x")
            assertThat(dir.mkdirs()).isTrue
            assertThat(File("${context.cacheDir}/x/a").createNewFile()).isTrue
            assertThat(File("${context.cacheDir}/x/b").createNewFile()).isTrue
            assertThat(File("${context.cacheDir}/x/c").createNewFile()).isTrue
            assertThat(File("${context.cacheDir}/y").createNewFile()).isTrue

            val scope =
                CommandInvocation(
                    "key",
                    "command",
                    "session_id",
                    mapOf("path" to CommandArgument.Text("x")),
                )
            assertThat(FileListCommand(context).execute(scope)).isEqualTo(
                CommandResult.Success(
                    context = mapOf("file_count" to "3", "contents" to "a\nb\nc"),
                ),
            )
        }
}
