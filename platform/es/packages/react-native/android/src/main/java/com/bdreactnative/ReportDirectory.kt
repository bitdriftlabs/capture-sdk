// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package com.bdreactnative

import android.content.Context
import android.util.Log
import io.bitdrift.capture.utils.SdkDirectory
import java.io.File

object ReportDirectory {
    private const val TAG = "BdReactNative"
    private const val WATCHER_DIR_PATH = "reports/watcher"

    /**
     * Returns the watcher directory path for JavaScript error reports
     */
    fun getWatcherDirectory(context: Context): File {
        return File(SdkDirectory.getPath(context), WATCHER_DIR_PATH)
    }

    /**
     * Sets up the report watcher directory based on whether JS errors are enabled
     * Creates the directory if enabled, removes it if disabled
     */
    fun setupWatcherDirectory(context: Context, enableJsErrors: Boolean) {
        runCatching {
            val watcherDir = getWatcherDirectory(context)

            if (enableJsErrors) {
                createWatcherDirectory(watcherDir)
            } else {
                removeWatcherDirectory(watcherDir)
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to handle directory modifications for enableJsErrors $enableJsErrors: $error")
        }
    }

    /**
     * Creates the watcher directory if it doesn't exist
     */
    private fun createWatcherDirectory(watcherDir: File) {
        if (!watcherDir.exists() && !watcherDir.mkdirs()) {
            Log.w(TAG, "Failed to create reports/watcher directory")
        }
    }

    /**
     * Removes the watcher directory if it exists
     */
    private fun removeWatcherDirectory(watcherDir: File) {
        if (watcherDir.exists() && !watcherDir.deleteRecursively()) {
            Log.w(TAG, "Failed to delete reports/watcher directory")
        }
    }
}

