// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.content.Context
import io.bitdrift.capture.EMPTY_INTERNAL_FIELDS
import io.bitdrift.capture.IPreferences
import io.bitdrift.capture.InternalFields
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.toFieldValue
import java.io.File

private const val LAST_APP_DISK_USAGE_EVENT_EMISSION_TIME = "lastAppDiskUsageEventEmissionTime"
private const val DAY_MS = 60 * 60 * 24 * 1_000

/**
 * Responsible for collecting information about disk usage. The information is collected
 * and reported back no more than once every 24 hours.
 */
internal class DiskUsageMonitor(
    private val preferences: IPreferences,
    private val context: Context,
    private val clock: IClock = DefaultClock.getInstance(),
) {
    var runtime: Runtime? = null

    fun getDiskUsage(): InternalFields {
        if (runtime?.isEnabled(RuntimeFeature.DISK_USAGE_FIELDS) == false) {
            return EMPTY_INTERNAL_FIELDS
        }

        val now = clock.elapsedRealtime()

        val lastEmission = preferences.getLong(LAST_APP_DISK_USAGE_EVENT_EMISSION_TIME)

        // The disk usage fields are emitted only once every 24 hours.
        if (lastEmission != null && (now - lastEmission < DAY_MS)) {
            return EMPTY_INTERNAL_FIELDS
        }

        val cacheDirSize = calculateCumulativeSize(context.cacheDir)
        val filesDirSize = calculateCumulativeSize(context.filesDir)

        val externalCacheDirSize = context.externalCacheDir?.let { calculateCumulativeSize(it) }
        val externalFilesDirSize = context.getExternalFilesDir(null)?.let { calculateCumulativeSize(it) }

        preferences.setLong(LAST_APP_DISK_USAGE_EVENT_EMISSION_TIME, now)

        return buildList {
            add(Field("_cache_dir_size_bytes", cacheDirSize.toString().toFieldValue()))
            add(Field("_files_dir_size_bytes", filesDirSize.toString().toFieldValue()))
            externalCacheDirSize?.let {
                add(Field("_external_cache_dir_size_bytes", it.toString().toFieldValue()))
            }
            externalFilesDirSize?.let {
                add(Field("_external_files_dir_size_bytes", it.toString().toFieldValue()))
            }
        }.toTypedArray()
    }

    private fun calculateCumulativeSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            file.listFiles()?.forEach { currentFile ->
                size += calculateCumulativeSize(currentFile)
            }
        } else {
            size = file.length()
        }
        return size
    }
}
