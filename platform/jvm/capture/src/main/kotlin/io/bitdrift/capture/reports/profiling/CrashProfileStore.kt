// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.profiling

import android.util.Log
import androidx.annotation.RequiresApi
import io.bitdrift.capture.Capture.LOG_TAG
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Stores SDK-owned raw profiles until native attribution consumes or retains them. */
@RequiresApi(API_ANR)
internal class CrashProfileStore(
    private val directory: File,
    private val fileOperations: CrashProfileFileOperations = CrashProfileFileOperations(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Borrows [source] only until return; the original is never renamed or deleted. */
    fun importProfile(
        source: File,
        kind: CrashProfileKind,
    ): File = stage(source, kind, owned = false)

    /** Transfers ownership, normally with a same-filesystem rename rather than a payload copy. */
    fun adopt(
        source: File,
        kind: CrashProfileKind,
    ): File = stage(source, kind, owned = true)

    fun pendingResults(): List<Pair<String, String>> =
        files()
            .mapNotNull { file ->
                CrashProfileKind.entries
                    .firstOrNull { file.name.endsWith(".${it.value}") || file.name.endsWith(RETAINED_SUFFIX + it.value) }
                    ?.takeIf { file.isFile }
                    ?.let { it.value to file.path }
            }

    fun validateSource(source: File) {
        require(!source.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath())) {
            "Crash profile must be outside Capture's private profile store"
        }
        require(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Crash profile must be a readable regular file"
        }
    }

    private fun stage(
        source: File,
        kind: CrashProfileKind,
        owned: Boolean,
    ): File {
        validateSource(source)
        val existing = files()
        val available = MAX_PENDING_BYTES - existing.sumOf { it.length() }
        if (source.length() > available) {
            throw IOException("Crash profile exceeds the pending profile storage budget")
        }
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create crash profile directory")
        }
        directory.parentFile?.let(fileOperations::syncDirectory)
        val target = File(directory, "${UUID.randomUUID()}.${kind.value}")
        if (owned) {
            try {
                fileOperations.move(source, target)
                if (!target.setLastModified(now())) throw IOException("Cannot timestamp adopted crash profile")
                fileOperations.syncFile(target)
                fileOperations.syncDirectory(directory)
                source.absoluteFile.parentFile?.let(fileOperations::syncDirectory)
                Log.d(LOG_TAG, "Adopted crash profile by rename: ${target.name}")
                return target
            } catch (e: AtomicMoveNotSupportedException) {
                Log.d(LOG_TAG, "Crash profile requires a cross-filesystem copy", e)
            }
        }

        val temporary = File(directory, "${target.name}.partial")
        var committed = false
        try {
            fileOperations.copy(source, temporary, available)
            fileOperations.syncFile(temporary)
            fileOperations.move(temporary, target)
            fileOperations.syncDirectory(directory)
            committed = true
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                Log.w(LOG_TAG, "Failed to remove partial crash profile: ${temporary.name}")
            }
            if (!committed && target.exists() && !target.delete()) {
                Log.w(LOG_TAG, "Failed to remove uncommitted crash profile: ${target.name}")
            }
        }
        if (owned) {
            Files.delete(source.toPath())
            source.absoluteFile.parentFile?.let(fileOperations::syncDirectory)
        }
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "Stored private crash profile copy: ${target.name}")
        }
        return target
    }

    private fun files(): List<File> {
        if (!directory.exists()) return emptyList()
        val files = directory.listFiles() ?: throw IOException("Cannot list crash profile directory")
        val cutoff = now() - MAX_AGE_MS
        return files.filter { file ->
            if (file.lastModified() >= cutoff) {
                true
            } else {
                if (!file.delete() && file.exists()) throw IOException("Cannot remove expired crash profile: ${file.name}")
                Log.d(LOG_TAG, "Removed expired crash profile: ${file.name}")
                false
            }
        }
    }

    internal companion object {
        const val DIRECTORY = "crash_profiles"
        const val MAX_PENDING_BYTES = 64L * 1024 * 1024
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        // Native attribution appends this suffix when the source process's report does not exist yet.
        const val RETAINED_SUFFIX = ".bitdrift-retained-"
    }

    /** Filesystem boundaries kept injectable so durability failures can be exercised without a mount. */
    @RequiresApi(API_ANR)
    internal open class CrashProfileFileOperations {
        open fun move(
            source: File,
            target: File,
        ) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }

        open fun copy(
            source: File,
            target: File,
            available: Long,
        ) {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        copied += read
                        if (copied > available) {
                            throw IOException("Crash profile exceeds the pending profile storage budget")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        }

        open fun syncFile(file: File) {
            FileInputStream(file).use { it.fd.sync() }
        }

        open fun syncDirectory(directory: File) {
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use {
                it.force(true)
            }
        }
    }
}
