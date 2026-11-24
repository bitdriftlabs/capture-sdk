// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.file

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class FileClearCommandTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.walkBottomUp().forEach { it.delete() }
    }

    @Test
    fun `clearing an empty cache`(): Unit =
        runBlocking {
            val scope = CommandInvocation("key", "command", "session_id", mapOf())
            assertThat(FileClearCommand(context).execute(scope)).isEqualTo(CommandResult.Success())
            assertThat(context.cacheDir.listFiles()?.size).isEqualTo(0)
        }

    @Test
    fun `clearing files from the cache`(): Unit =
        runBlocking {
            val dir = File("${context.cacheDir}/x")
            assertThat(dir.mkdirs()).isTrue
            assertThat(File("${context.cacheDir}/x/a").createNewFile()).isTrue

            val scope = CommandInvocation("key", "command", "session_id", mapOf())
            assertThat(FileClearCommand(context).execute(scope)).isEqualTo(CommandResult.Success())
            assertThat(context.cacheDir.listFiles()?.size).isEqualTo(0)
        }
}
