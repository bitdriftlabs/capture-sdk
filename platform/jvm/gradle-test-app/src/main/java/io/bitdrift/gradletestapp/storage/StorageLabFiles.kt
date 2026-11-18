// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.storage

import java.io.File

/** Disposable fixtures, confined to one StorageLab directory. Reads never recreate deleted data. */
internal class StorageLabFiles(
    root: File,
) {
    private val root = root.canonicalFile

    data class Entry(
        val path: String,
        val directory: Boolean,
        val bytes: Long,
    )

    fun entries(): List<Entry> {
        if (!root.exists()) return emptyList()
        return root
            .walkTopDown()
            .onEnter { it.canonicalFile == it.absoluteFile }
            .filter { it != root && it.canonicalFile == it.absoluteFile }
            .map { Entry(it.relativeTo(root).invariantSeparatorsPath, it.isDirectory, if (it.isFile) it.length() else 0) }
            .toList()
    }

    fun create(
        path: String,
        sizeKiB: Int,
        directory: Boolean,
    ) {
        val target = child(path)
        require(!target.exists()) { "That path already exists. Choose another name or delete it first." }
        require(sizeKiB in 1..10240) { "Use a file size from 1 to 10240 KiB." }
        require(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "Cannot create parent directory." }
        if (directory) {
            check(target.mkdir()) { "Cannot create directory." }
        } else {
            target.outputStream().use { output ->
                val chunk = ByteArray(1024) { 65 }
                repeat(sizeKiB) { output.write(chunk) }
            }
        }
    }

    fun delete(path: String) {
        val target = child(path)
        require(target.exists()) { "This path no longer exists. Refresh the tree." }
        check(target.deleteRecursively()) { "Could not delete all contents." }
    }

    private fun child(path: String): File {
        val parts = path.split('/')
        require(parts.none { it.isEmpty() || it == "." || it == ".." }) { "Use a relative path without . or .. components." }
        var target = root
        for (part in parts) {
            target = File(target, part)
            require(target.canonicalFile == target.absoluteFile) { "Paths through symbolic links are not supported." }
        }
        require(target.canonicalPath.startsWith(root.path + File.separator)) { "Path must stay inside StorageLab." }
        return target
    }
}
