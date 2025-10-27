// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.papa

import io.bitdrift.capture.Capture
import papa.AppLaunchType
import papa.PapaEvent
import papa.PapaEventListener
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object PapaTelemetry {
    fun install() {
        PapaEventListener.install { event ->
            when (event) {
                is PapaEvent.AppLaunch -> {
                    Capture.Logger.logInfo(
                        mapOf(
                            "preLaunchState" to event.preLaunchState.toString(),
                            "durationMs" to event.durationUptimeMillis.toString(),
                            "isSlowLaunch" to event.isSlowLaunch.toString(),
                            "trampolined" to event.trampolined.toString(),
                            "backgroundDurationMs" to event.invisibleDurationRealtimeMillis.toString(),
                            "startUptimeMs" to event.startUptimeMillis.toString(),
                        ),
                    ) { "PapaEvent.AppLaunch" }
                    if (event.preLaunchState.launchType == AppLaunchType.COLD) {
                        Capture.Logger.logAppLaunchTTI(event.durationUptimeMillis.toDuration(DurationUnit.MILLISECONDS))
                    }
                }
                is PapaEvent.FrozenFrameOnTouch -> {
                    Capture.Logger.logInfo(
                        mapOf(
                            "activityName" to event.activityName,
                            "repeatTouchDownCount" to event.repeatTouchDownCount.toString(),
                            "handledElapsedMs" to event.deliverDurationUptimeMillis.toString(),
                            "frameElapsedMs" to event.dislayDurationUptimeMillis.toString(),
                            "pressedView" to event.pressedView.orEmpty(),
                        ),
                    ) { "PapaEvent.FrozenFrameOnTouch" }
                }
                is PapaEvent.UsageError -> {
                    Capture.Logger.logInfo(
                        mapOf(
                            "debugMessage" to event.debugMessage,
                        ),
                    ) { "PapaEvent.UsageError" }
                }
            }
        }
    }
}
