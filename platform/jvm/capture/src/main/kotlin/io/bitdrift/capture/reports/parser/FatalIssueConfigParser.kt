// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.parser

import android.content.Context
import java.io.File

/**
 * Parse the runtime config into valid path/extension.
 *
 * NOTE: This is only applicable for [io.bitdrift.capture.reports.FatalIssueMechanism.Integration]
 */
internal object FatalIssueConfigParser {
    private const val CACHE_DIR_PLACE_HOLDER = "{cache_dir}"
    private const val FILES_DIR_PLACE_HOLDER = "{files_dir}"
    private const val DATA_DIR_PLACE_HOLDER = "{data_dir}"

    /**
     * Sanitizes the configuration content to valid path/extension file
     */
    fun getFatalIssueConfigDetails(
        appContext: Context,
        crashConfigFileContent: String,
    ): FatalIssueConfigDetails? =
        runCatching {
            val crashConfigDetails = crashConfigFileContent.split(",")
            val sdkDirectoryPath = sanitizeConfigPath(appContext, crashConfigDetails[0])
            val fileExtension = crashConfigDetails[1].trim()
            FatalIssueConfigDetails(File(sdkDirectoryPath), fileExtension)
        }.getOrNull()

    /**
     * Sanitizes config path. If the config path doesn't contain placeholders it will fallback to
     * the default path provided
     */
    private fun sanitizeConfigPath(
        appContext: Context,
        originalConfigPath: String,
    ): String {
        val sanitizedPath = originalConfigPath.trim()
        return if (sanitizedPath.contains(CACHE_DIR_PLACE_HOLDER)) {
            sanitizedPath.replace(CACHE_DIR_PLACE_HOLDER, appContext.cacheDir.absolutePath)
        } else if (sanitizedPath.contains(FILES_DIR_PLACE_HOLDER)) {
            sanitizedPath.replace(FILES_DIR_PLACE_HOLDER, appContext.filesDir.absolutePath)
        } else if (sanitizedPath.contains(DATA_DIR_PLACE_HOLDER)) {
            sanitizedPath.replace(DATA_DIR_PLACE_HOLDER, appContext.dataDir.absolutePath)
        } else {
            sanitizedPath
        }
    }
}

/**
 * Holds the [FatalIssue] runtime configuration details
 */
internal data class FatalIssueConfigDetails(
    val sourceDirectory: File,
    val extensionFileName: String,
)
