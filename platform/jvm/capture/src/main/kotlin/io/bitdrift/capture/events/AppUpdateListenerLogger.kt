// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events

import android.content.Context
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import java.io.File
import java.util.concurrent.ExecutorService
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

internal class AppUpdateListenerLogger(
    private val logger: LoggerImpl,
    private val clientAttributes: ClientAttributes,
    private val context: Context,
    private val runtime: Runtime,
    private val executor: ExecutorService,
) : IEventListenerLogger {

    override fun start() {
        executor.execute {
            maybeLogAppUpdate(clientAttributes.appVersion, clientAttributes.appVersionCode)
        }
    }

    @Suppress("EmptyFunctionBlock")
    override fun stop() {}

    // Internal for tests purposes only.
    private fun maybeLogAppUpdate(appVersion: String, appVersionCode: Long) {
        if (!runtime.isEnabled(RuntimeFeature.APP_UPDATE_EVENTS)) {
            return
        }

        if (!logger.shouldLogAppUpdate(appVersion, appVersionCode)) {
            return
        }

        val timedValue = measureTimedValue {
            val baseAPK = File(this.context.applicationInfo.sourceDir)
            baseAPK.length()
        }

        logger.logAppUpdate(
            appVersion,
            appVersionCode,
            timedValue.value,
            timedValue.duration.toDouble(DurationUnit.SECONDS),
        )
    }
}
