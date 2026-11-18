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
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

/** Stores SDK-owned raw profiles until native attribution consumes or retains them. */
@RequiresApi(API_ANR)
internal class CrashProfileStore(
    private val directory: File,
    private val runtime: Runtime,
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
            .mapNotNull { (file, _) ->
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
        val sourceBytes = Files.size(source.toPath())
        val maxBytes =
            minOf(runtime.getConfigValue(RuntimeConfig.CHUNKED_ARTIFACT_MAX_BYTES).toLong(), MAX_CHUNKED_BYTES)
        val pendingBytes = files().sumOf { it.second }
        val available = maxBytes - pendingBytes
        Log.d(
            LOG_TAG,
            "Crash profile admission: kind=${kind.value}, owned=$owned, sourceBytes=$sourceBytes, " +
                "maxBytes=$maxBytes, pendingBytes=$pendingBytes, availableBytes=$available",
        )
        if (sourceBytes > available || sourceBytes == 0L) {
            val limit = if (sourceBytes > maxBytes) "attachment size limit" else "pending profile storage budget"
            throw IOException("Crash profile exceeds the $limit")
        }
        Files.createDirectories(directory.toPath())
        directory.parentFile?.let(fileOperations::syncDirectory)
        val target = File(directory, "${UUID.randomUUID()}.${kind.value}")
        val temporary = File(directory, "${target.name}.partial")
        val renamed: Boolean
        var committed = false
        try {
            // Even a zero-copy adoption must not be replayable before validation and file sync.
            renamed = owned && tryStageByRename(source, temporary)
            if (renamed) {
                if (temporary.length() != sourceBytes) throw IOException("Crash profile changed during adoption")
                if (!temporary.setLastModified(now())) throw IOException("Cannot timestamp adopted crash profile")
            } else {
                fileOperations.copy(source, temporary, available)
                if (temporary.length() != sourceBytes || Files.size(source.toPath()) != sourceBytes) {
                    throw IOException("Crash profile changed during import")
                }
            }
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
            if (!renamed) Files.delete(source.toPath())
            source.absoluteFile.parentFile?.let(fileOperations::syncDirectory)
        }
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "Stored private crash profile: ${target.name}, renamed=$renamed")
        }
        return target
    }

    private fun tryStageByRename(
        source: File,
        temporary: File,
    ): Boolean =
        try {
            fileOperations.move(source, temporary)
            true
        } catch (e: AtomicMoveNotSupportedException) {
            // Only staging the original can fall back to a copy; publication must stay atomic.
            Log.d(LOG_TAG, "Crash profile requires a cross-filesystem copy", e)
            false
        }

    private fun files(): List<Pair<File, Long>> =
        try {
            scanFiles()
        } catch (e: NoSuchFileException) {
            // A missing entry may have been renamed by native retention, not deleted. Re-list once
            // so its new name and bytes are included. If the second scan also races, fail rather
            // than undercount the budget or keep retrying on the shared SDK executor.
            Log.d(LOG_TAG, "Retrying crash profile inventory after an entry disappeared", e)
            scanFiles()
        }

    private fun scanFiles(): List<Pair<File, Long>> {
        if (!directory.exists()) return emptyList()
        val files = directory.listFiles() ?: throw IOException("Cannot list crash profile directory")
        val cutoff = now() - MAX_AGE_MS
        // TODO: Reclaim abandoned .partial files before age-based expiry. First coordinate with
        // live writers across processes; a serial executor only protects this process's writer.
        return files.mapNotNull { file ->
            val attributes = fileOperations.readAttributes(file)
            if (attributes.lastModifiedTime().toMillis() >= cutoff) {
                file to attributes.size()
            } else {
                Files.delete(file.toPath())
                Log.d(LOG_TAG, "Removed expired crash profile: ${file.name}")
                null
            }
        }
    }

    internal companion object {
        const val DIRECTORY = "crash_profiles"
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_CHUNKED_BYTES = 256L * 1024 * 1024

        // Native attribution appends this suffix when the source process's report does not exist yet.
        const val RETAINED_SUFFIX = ".bitdrift-retained-"
    }

    /** Filesystem boundaries kept injectable so durability failures can be exercised without a mount. */
    @RequiresApi(API_ANR)
    internal open class CrashProfileFileOperations {
        open fun readAttributes(file: File): BasicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)

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
