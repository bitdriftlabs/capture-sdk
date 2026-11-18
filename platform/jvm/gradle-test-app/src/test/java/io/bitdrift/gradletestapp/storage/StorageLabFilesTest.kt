// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class StorageLabFilesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun exactSizesAndNestedPaths() {
        val root = File(temporary.root, "StorageLab")
        val lab = StorageLabFiles(root)
        lab.create("images/icons/logo.txt", 4, false)
        val entries = lab.entries().associateBy { it.path }
        assertTrue(entries.getValue("images").directory)
        assertTrue(entries.getValue("images/icons").directory)
        assertEquals(4096L, entries.getValue("images/icons/logo.txt").bytes)
        assertThrows(IllegalArgumentException::class.java) { lab.create("images/icons/logo.txt", 1, false) }
        // Equal names in different parents are legitimate distinct directories.
        lab.create("icons", 1, true)
        assertTrue(File(root, "icons").isDirectory)
        lab.delete("images")
        assertFalse(File(root, "images").exists())
        assertTrue(File(root, "icons").exists())
    }

    @Test
    fun refreshAfterRemoteDeletionDoesNotRecreateFiles() {
        val root = File(temporary.root, "StorageLab")
        val lab = StorageLabFiles(root)
        lab.create("sample.txt", 1, false)
        assertTrue(root.deleteRecursively())
        assertTrue(lab.entries().isEmpty())
        assertFalse(root.exists())
    }

    @Test
    @Suppress("NewApi") // JVM unit test: java.nio.file is not executed on an Android device.
    fun traversalAndSymlinksCannotEscapeLab() {
        val root = temporary.newFolder("StorageLab")
        val outside = temporary.newFolder("outside")
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        val lab = StorageLabFiles(root)
        for (path in listOf("", "/absolute", "..", "../outside/file", "nested/../../outside", "link/file")) {
            assertThrows(IllegalArgumentException::class.java) { lab.create(path, 1, false) }
            assertThrows(IllegalArgumentException::class.java) { lab.delete(path) }
        }
        assertTrue(outside.listFiles().orEmpty().isEmpty())
        assertTrue(lab.entries().isEmpty())
    }

    @Test
    fun invalidSizesDoNotCreateDirectories() {
        val root = File(temporary.root, "StorageLab")
        val lab = StorageLabFiles(root)
        for (size in listOf(0, -1, 10241)) {
            assertThrows(IllegalArgumentException::class.java) { lab.create("nested/file.txt", size, false) }
        }
        assertFalse(root.exists())
    }
}
