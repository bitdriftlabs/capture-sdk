// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.init

import android.app.Application
import io.bitdrift.capture.commands.CommandResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SampleCommandsTest {
    @get:Rule val temporary = TemporaryFolder()

    private val filesDir by lazy { temporary.newFolder("files") }
    private val profilesDir by lazy { File(filesDir, "profiling").apply { mkdir() } }
    private val source by lazy {
        File(profilesDir, "saved.perfetto-trace").apply {
            outputStream().use { output ->
                val buffer = ByteArray(8192) { it.toByte() }
                repeat(256) { output.write(buffer) }
            }
        }
    }

    @Test
    fun streamsADedicatedFileAndCompletesE2eExportBeforeHandoff() =
        runTest {
            val exportDir = temporary.newFolder("e2e")
            val exporter = ProfileExporter("test.app", { profilesDir }, { exportDir }, ProfileExporter.AdbAccess.EXPORT)
            var exported: File? = null
            val result =
                SampleCommands.profileResult(
                    AppProfiler.ProfilingType.JAVA_HEAP_DUMP,
                    5000,
                    AppProfiler.Result.Saved(source.path),
                    filesDir,
                ) { file ->
                    assertTrue(exporter.export(file) is ProfileExporter.Result.Exported)
                    val copy = File(exportDir, file.name)
                    exported = copy
                    assertEquals(source.digest().toList(), copy.digest().toList())
                } as CommandResult.Success

            val attachment = checkNotNull(result.attachment)
            val owned = checkNotNull(attachment.file)
            assertTrue(attachment.bytes.isEmpty())
            assertEquals("application/vnd.bitdrift.perfetto-trace", attachment.contentType)
            assertEquals(source.length().toString(), result.context["trace_bytes"])
            assertEquals("JAVA_HEAP_DUMP", result.context["profiling_type"])
            assertFalse(result.context.containsKey("duration_ms"))
            assertEquals(filesDir, owned.parentFile)
            assertNotEquals(source, owned)
            assertEquals(source.digest().toList(), owned.digest().toList())
            assertTrue(checkNotNull(exported).isFile)

            // SDK consumption cannot invalidate the platform file used by manual/bulk export.
            assertTrue(owned.delete())
            assertTrue(exporter.exportAll() is ProfileExporter.Result.Exported)
            assertEquals(source.digest().toList(), File(exportDir, source.name).digest().toList())
        }

    @Test
    fun cancellationDuringExportRemovesOnlyTheUnclaimedAttachment() =
        runTest {
            val original = source
            val entered = CompletableDeferred<Unit>()
            val never = CompletableDeferred<Unit>()
            val preparation =
                async {
                    SampleCommands.profileResult(
                        AppProfiler.ProfilingType.HEAP_PROFILE,
                        2000,
                        AppProfiler.Result.Saved(original.path),
                        filesDir,
                    ) {
                        entered.complete(Unit)
                        never.await()
                    }
                }
            entered.await()

            preparation.cancelAndJoin()

            assertTrue(original.exists())
            assertEquals(listOf(profilesDir), filesDir.listFiles()!!.toList())
        }

    @Test
    fun exportFailureCannotReturnOrLeakAnAttachment() =
        runTest {
            val result =
                SampleCommands.profileResult(
                    AppProfiler.ProfilingType.SYSTEM_TRACE,
                    2000,
                    AppProfiler.Result.Saved(source.path),
                    filesDir,
                ) { throw IOException("export storage full") } as CommandResult.Error

            assertEquals("profiling_failed", result.title)
            assertEquals("export storage full", result.description)
            assertEquals(listOf(profilesDir), filesDir.listFiles()!!.toList())
        }

    @Test
    fun missingSavedFileIsAnExplicitFailure() =
        runTest {
            val result =
                SampleCommands.profileResult(
                    AppProfiler.ProfilingType.JAVA_HEAP_DUMP,
                    null,
                    AppProfiler.Result.Saved(File(filesDir, "missing").path),
                    filesDir,
                ) as CommandResult.Error

            assertEquals("profiling_failed", result.title)
            assertEquals("trace file missing", result.description)
            assertTrue(filesDir.listFiles()!!.isEmpty())
        }

    @Test
    fun fixtureReplayTransfersOnlyADedicatedCopy() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val input = File(checkNotNull(context.getExternalFilesDir("e2e-inputs")), "fixture.bin")
            source.copyTo(input)
            val attachment = E2eCommands.replayFile(context, input.name, "application/octet-stream")
            val owned = checkNotNull(attachment.file)

            assertTrue(attachment.bytes.isEmpty())
            assertEquals(source.digest().toList(), owned.digest().toList())
            assertEquals(context.filesDir, owned.parentFile)
            assertTrue(owned.delete())
            assertEquals(source.digest().toList(), input.digest().toList())
        }

    @Test
    fun fixtureReplayRejectsFilesOutsideTheFixtureDirectory() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val inputs = checkNotNull(context.getExternalFilesDir("e2e-inputs"))
            val outside = File(checkNotNull(inputs.parentFile), "outside.bin")
            source.copyTo(outside)

            try {
                E2eCommands.replayFile(context, "../outside.bin", "application/octet-stream")
                org.junit.Assert.fail("expected fixture confinement failure")
            } catch (e: IOException) {
                assertTrue(checkNotNull(e.message).contains("directly under e2e-inputs"))
            }
            assertTrue(outside.isFile)
            assertTrue(
                context.filesDir
                    .listFiles()
                    .orEmpty()
                    .none { it.name.startsWith("e2e-attachment-") },
            )
        }

    private fun File.digest(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }
}
