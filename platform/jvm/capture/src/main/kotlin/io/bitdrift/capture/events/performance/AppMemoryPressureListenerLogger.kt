// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger
import io.bitdrift.capture.providers.toFields
import java.util.concurrent.ExecutorService

internal class AppMemoryPressureListenerLogger(
    private val logger: LoggerImpl,
    private val context: Context,
    private val memoryMonitor: MemoryMonitor,
    private val runtime: Runtime,
    private val executor: ExecutorService,
) : IEventListenerLogger,
    ComponentCallbacks2 {
    // TODO(murki): Remove the usage of these fields altogether
    @Suppress("DEPRECATION")
    private fun getTrimLevelAsString(level: Int): String =
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "TRIM_MEMORY_BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "TRIM_MEMORY_RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "TRIM_MEMORY_RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "TRIM_MEMORY_RUNNING_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "TRIM_MEMORY_UI_HIDDEN"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "TRIM_MEMORY_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "TRIM_MEMORY_COMPLETE"
            else -> level.toString()
        }

    private fun extraMemFields(level: Int): Map<String, String> {
        val fields =
            mutableMapOf(
                "_trim_level" to getTrimLevelAsString(level),
            )

        fields.putAll(memoryMonitor.getMemoryAttributes())

        return fields
    }

    override fun start() {
        context.registerComponentCallbacks(this)
    }

    override fun stop() {
        context.unregisterComponentCallbacks(this)
    }

    override fun onTrimMemory(level: Int) {
        executor.execute {
            if (!runtime.isEnabled(RuntimeFeature.APP_MEMORY_PRESSURE)) {
                return@execute
            }
            // refer to levels https://developer.android.com/reference/android/content/ComponentCallbacks2
            logger.log(
                LogType.LIFECYCLE,
                LogLevel.WARNING,
                extraMemFields(level).toFields(),
            ) { "AppMemTrim" }
        }
    }

    override fun onConfigurationChanged(p0: Configuration) {
        // no-op
    }

    override fun onLowMemory() {
        // no-op
    }
}
