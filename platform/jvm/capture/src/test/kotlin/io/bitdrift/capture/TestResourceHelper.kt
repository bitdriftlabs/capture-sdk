// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Helper object to load test resources in a way that works with both Bazel and Gradle.
 *
 * - Bazel: Uses TEST_SRCDIR environment variable to locate source files
 * - Gradle: Uses ClassLoader to load resources from the test resources directory
 */
object TestResourceHelper {
    /**
     * Loads a test resource file as an InputStream.
     *
     * @param relativePath The path relative to the test resources directory.
     *                     Example: "app_exit_anr_deadlock_anr.txt"
     * @return InputStream for the resource file
     * @throws IllegalStateException if the resource cannot be found
     */
    fun getResourceAsStream(relativePath: String): InputStream {
        // Try Bazel approach first (TEST_SRCDIR environment variable)
        val testSrcDir = System.getenv("TEST_SRCDIR")
        if (testSrcDir != null) {
            val file =
                Paths
                    .get(
                        testSrcDir,
                        "_main",
                        "platform/jvm/capture/src/test/resources",
                        relativePath,
                    ).toFile()

            if (file.exists()) {
                return file.inputStream()
            }
        }

        // Gradle approach: use ClassLoader to find resources
        val classLoader = TestResourceHelper::class.java.classLoader
        val stream = classLoader?.getResourceAsStream(relativePath)
        if (stream != null) {
            return stream
        }

        // If neither approach works, throw an error
        throw IllegalStateException(
            "Could not find test resource: $relativePath. " +
                "TEST_SRCDIR=$testSrcDir, ClassLoader search failed.",
        )
    }

    /**
     * Gets the Path to a test resource file.
     *
     * Note: This only works reliably with Bazel (TEST_SRCDIR).
     * For Gradle, resources are packaged in JARs and may not have file system paths.
     *
     * @param relativePath The path relative to the test resources directory
     * @return Path to the resource file
     * @throws IllegalStateException if running under Gradle or resource not found
     */
    fun getResourcePath(relativePath: String): Path {
        val testSrcDir = System.getenv("TEST_SRCDIR")
        if (testSrcDir != null) {
            return Paths.get(
                testSrcDir,
                "_main",
                "platform/jvm/capture/src/test/resources",
                relativePath,
            )
        }

        throw IllegalStateException(
            "getResourcePath() only works with Bazel (TEST_SRCDIR). " +
                "Use getResourceAsStream() instead for Gradle compatibility.",
        )
    }
}
