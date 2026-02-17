// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.model

import io.bitdrift.capture.LogLevel

/** Namespace feature actions under a sealed AppAction interface */
sealed interface AppAction

sealed class ConfigAction : AppAction {
    object InitializeSdk : ConfigAction()

    data class UpdateApiKey(
        val apiKey: String,
    ) : ConfigAction()

    data class UpdateApiUrl(
        val apiUrl: String,
    ) : ConfigAction()

    data class UpdateLogLevel(
        val logLevel: LogLevel,
    ) : ConfigAction()

    data class SetSleepModeEnabled(
        val enabled: Boolean,
    ) : ConfigAction()
}

sealed class SessionAction : AppAction {
    object StartNewSession : SessionAction()

    object GenerateDeviceCode : SessionAction()

    object CopySessionUrl : SessionAction()
}

sealed class DiagnosticsAction : AppAction {
    object LogMessage : DiagnosticsAction()

    object ForceAppExit : DiagnosticsAction()

    data class UpdateAppExitReason(
        val reason: AppExitReason,
    ) : DiagnosticsAction()
}

sealed class NetworkTestAction : AppAction {
    object PerformOkHttpRequest : NetworkTestAction()

    object PerformGraphQlRequest : NetworkTestAction()

    object PerformRetrofitRequest : NetworkTestAction()
}

sealed class FeatureFlagsTestAction : AppAction {
    data class AddVariantFlag(val value: Boolean) : FeatureFlagsTestAction()

    object AddManyFeatureFlags : FeatureFlagsTestAction()
}

sealed class NavigationAction : AppAction {
    object NavigateToConfig : NavigationAction()

    data class NavigateToWebView(val url: String) : NavigationAction()

    object NavigateToCompose : NavigationAction()

    object NavigateToXml : NavigationAction()

    object NavigateToDialogAndModals : NavigationAction()

    object NavigateToStressTest : NavigationAction()

    object InvokeService : NavigationAction()
}

sealed class StressTestAction : AppAction {
    data class IncreaseMemoryPressure(val targetPercent: Int) : StressTestAction()

    object TriggerMemoryPressureAnr : StressTestAction()

    data class TriggerJankyFrames(val type: JankType) : StressTestAction()

    object TriggerStrictModeViolation : StressTestAction()
}

sealed class GlobalFieldAction : AppAction {
    data class AddFieldAction(val key: String, val value: String) : AppAction

    data class RemoveFieldKey(val key: String) : AppAction
}

object ClearError : AppAction

enum class JankType(val displayName: String, val durationMs: Long) {
    SLOW("Slow (200ms)", 200),
    FROZEN("Frozen (800ms)", 800),
    ANR("ANR (6000ms)", 6000),
}
