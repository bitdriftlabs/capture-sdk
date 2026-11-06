// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.profiling

import io.bitdrift.capture.reports.profiling.CrashProfileStore.CrashProfileFileOperations
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashProfileStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val now = System.currentTimeMillis()
    private val directory get() = File(temporary.root, "profiles")

    private fun store() = CrashProfileStore(directory) { now }

    @Test
    fun importingBorrowsTheOriginalAndPersistsAnIndependentCopy() {
        val source = temporary.newFile("borrowed").apply { writeText("profile") }
        val originalModified = source.lastModified()

        val imported = store().importProfile(source, CrashProfileKind.ANR)

        assertThat(source.readText()).isEqualTo("profile")
        assertThat(source.lastModified()).isEqualTo(originalModified)
        assertThat(imported.readText()).isEqualTo("profile")
        source.writeText("changed")
        assertThat(imported.readText()).isEqualTo("profile")
        assertThat(store().pendingResults()).containsExactly("anr" to imported.path)
    }

    @Test
    fun adoptionRenamesWithoutCopyingFileContents() {
        val source = temporary.newFile("owned").apply { writeText("profile") }
        val identity = Files.readAttributes(source.toPath(), "unix:ino")["ino"]

        val adopted = store().adopt(source, CrashProfileKind.OOM)

        assertThat(source).doesNotExist()
        assertThat(adopted.readText()).isEqualTo("profile")
        assertThat(Files.readAttributes(adopted.toPath(), "unix:ino")["ino"]).isEqualTo(identity)
        assertThat(store().pendingResults()).containsExactly("oom" to adopted.path)
    }

    @Test
    fun crossFilesystemAdoptionCopiesSyncsAndPublishesBeforeDeletingItsOriginal() {
        val source = temporary.newFile("cross-device").apply { writeText("profile") }
        val original = source
        val events = mutableListOf<String>()
        val operations =
            object : CrashProfileFileOperations() {
                override fun move(
                    source: File,
                    target: File,
                ) {
                    if (source == original) {
                        events += "cross-device"
                        throw AtomicMoveNotSupportedException(source.path, target.path, "different devices")
                    }
                    assertThat(source.readText()).isEqualTo("profile")
                    events += "publish"
                    super.move(source, target)
                }

                override fun copy(
                    source: File,
                    target: File,
                    available: Long,
                ) {
                    events += "copy"
                    super.copy(source, target, available)
                }

                override fun syncFile(file: File) {
                    events += "sync"
                    super.syncFile(file)
                }

                override fun syncDirectory(directory: File) {
                    if (directory == this@CrashProfileStoreTest.directory) {
                        assertThat(source).exists()
                        events += "directory"
                    }
                    super.syncDirectory(directory)
                }
            }

        val adopted = CrashProfileStore(directory, operations).adopt(source, CrashProfileKind.ANR)

        assertThat(events).containsExactly("cross-device", "copy", "sync", "publish", "directory")
        assertThat(source).doesNotExist()
        assertThat(adopted.readText()).isEqualTo("profile")
        assertThat(store().pendingResults()).containsExactly("anr" to adopted.path)
    }

    @Test
    fun copySyncAndPublicationFailuresNeverModifyBorrowedSourcesOrExposePartialResults() {
        listOf("copy", "sync", "publish", "directory").forEach { failing ->
            val source = temporary.newFile("source-$failing").apply { writeText("profile") }
            val modified = source.lastModified()
            val operations =
                object : CrashProfileFileOperations() {
                    override fun copy(
                        source: File,
                        target: File,
                        available: Long,
                    ) {
                        if (failing == "copy") {
                            target.writeText("incomplete")
                            throw IOException("copy failed")
                        }
                        super.copy(source, target, available)
                    }

                    override fun syncFile(file: File) {
                        if (failing == "sync") throw IOException("sync failed")
                        super.syncFile(file)
                    }

                    override fun move(
                        source: File,
                        target: File,
                    ) {
                        if (failing == "publish") throw IOException("publish failed")
                        super.move(source, target)
                    }

                    override fun syncDirectory(directory: File) {
                        if (failing == "directory" && directory == this@CrashProfileStoreTest.directory) {
                            throw IOException("directory failed")
                        }
                        super.syncDirectory(directory)
                    }
                }

            assertThatThrownBy { CrashProfileStore(directory, operations).importProfile(source, CrashProfileKind.ANR) }
                .isInstanceOf(IOException::class.java)
                .hasMessage("$failing failed")
            assertThat(source.readText()).isEqualTo("profile")
            assertThat(source.lastModified()).isEqualTo(modified)
            assertThat(directory.listFiles()).isEmpty()
            assertThat(store().pendingResults()).isEmpty()
        }
    }

    @Test
    fun failedCrossFilesystemCopyLeavesOwnedSourceAvailableForCallerCleanup() {
        val source = temporary.newFile("cross-device-failure").apply { writeText("profile") }
        val operations =
            object : CrashProfileFileOperations() {
                override fun move(
                    source: File,
                    target: File,
                ): Unit = throw AtomicMoveNotSupportedException(source.path, target.path, "different devices")

                override fun syncFile(file: File): Unit = throw IOException("disk failure")
            }

        assertThatThrownBy { CrashProfileStore(directory, operations).adopt(source, CrashProfileKind.OOM) }
            .isInstanceOf(IOException::class.java)
            .hasMessage("disk failure")
        assertThat(source.readText()).isEqualTo("profile")
        assertThat(directory.listFiles()).isEmpty()
    }

    @Test
    fun nativeRetentionIsRediscoveredAfterRestart() {
        val imported = store().importProfile(temporary.newFile(), CrashProfileKind.ANR)
        val retained = File(directory, imported.name + CrashProfileStore.RETAINED_SUFFIX + "anr")
        Files.move(imported.toPath(), retained.toPath())

        assertThat(store().pendingResults()).containsExactly("anr" to retained.path)
    }

    @Test
    fun cleanupIsLimitedToSdkStorageAndHasAnExactAgeBoundary() {
        directory.mkdirs()
        val expired =
            File(directory, "expired.anr").apply {
                writeText("old")
                setLastModified(now - CrashProfileStore.MAX_AGE_MS - 1)
            }
        val boundary =
            File(directory, "boundary.oom").apply {
                writeText("keep")
                setLastModified(now - CrashProfileStore.MAX_AGE_MS)
            }
        val partial =
            File(directory, "incomplete.anr.partial").apply {
                writeText("partial")
                setLastModified(now - CrashProfileStore.MAX_AGE_MS - 1)
            }
        val host =
            temporary.newFile("host").apply {
                writeText("host")
                setLastModified(now - CrashProfileStore.MAX_AGE_MS - 1)
            }

        assertThat(store().pendingResults()).containsExactly("oom" to boundary.path)
        assertThat(expired).doesNotExist()
        assertThat(partial).doesNotExist()
        assertThat(host.readText()).isEqualTo("host")
    }

    @Test
    fun pendingStorageBudgetAllowsTheBoundaryAndRejectsAnOverflowWithoutTouchingTheSource() {
        directory.mkdirs()
        RandomAccessFile(File(directory, "existing.oom"), "rw").use {
            it.setLength(CrashProfileStore.MAX_PENDING_BYTES - 10)
        }
        val boundary = temporary.newFile("boundary").apply { writeBytes(ByteArray(10)) }
        store().importProfile(boundary, CrashProfileKind.ANR)
        val overflow = temporary.newFile("overflow").apply { writeText("x") }

        assertThatThrownBy { store().importProfile(overflow, CrashProfileKind.ANR) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("budget")
        assertThat(overflow.readText()).isEqualTo("x")
        assertThat(boundary.length()).isEqualTo(10)
        assertThat(directory.listFiles()!!.sumOf { it.length() }).isEqualTo(CrashProfileStore.MAX_PENDING_BYTES)
    }

    @Test
    fun invalidSourcesAndPrivateStorePathsAreRejectedWithoutDeletingAnything() {
        val source = temporary.newFile().apply { writeText("profile") }
        val imported = store().importProfile(source, CrashProfileKind.ANR)

        assertThatThrownBy { store().importProfile(imported, CrashProfileKind.ANR) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { store().importProfile(temporary.root, CrashProfileKind.ANR) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { store().importProfile(File(temporary.root, "missing"), CrashProfileKind.ANR) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(imported.readText()).isEqualTo("profile")
        assertThat(source.readText()).isEqualTo("profile")
    }
}
