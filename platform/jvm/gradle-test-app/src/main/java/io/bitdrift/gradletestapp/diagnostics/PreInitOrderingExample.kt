// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.gradletestapp.diagnostics

import io.bitdrift.capture.Capture
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.PreInitInMemoryLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

internal object PreInitOrderingExample {
    private const val FIELD = "pre_init_order"
    private const val MAX_UPDATES = 200

    suspend fun run(): String = withContext(Dispatchers.IO) {
        val startupLogger = Capture.logger() as? PreInitInMemoryLogger
            ?: return@withContext "Enable slow background startup, restart, then tap this button while the SDK is starting"
        var lastValue = "0"
        try {
            Timber.i("Pre-init ordering: exercising the actual Capture startup buffer")
            withTimeout(60_000) {
                var update = 0
                do {
                    if (update < MAX_UPDATES) {
                        lastValue = update.toString()
                        update++
                        startupLogger.addField(FIELD, lastValue)
                        startupLogger.log(
                            LogLevel.INFO,
                            fields = mapOf("expected_pre_init_order" to lastValue),
                        ) { "Pre-init ordering checkpoint" }
                        Timber.i("Pre-init ordering: submitted %s=%s", FIELD, lastValue)
                    }
                    delay(50)
                    check(Capture.logger() != null) { "SDK startup failed during ordering example" }
                } while (Capture.Logger.sessionId == null)
            }

            // Do not write the field again: that would hide a stale update replayed during handoff.
            Capture.Logger.logInfo(mapOf("expected_pre_init_order" to lastValue)) {
                "Pre-init ordering result"
            }
            val result = "Ordering example finished: pre_init_order should equal $lastValue. Check the timeline."
            Timber.i(result)
            result
        } finally {
            startupLogger.removeField(FIELD)
        }
    }
}
