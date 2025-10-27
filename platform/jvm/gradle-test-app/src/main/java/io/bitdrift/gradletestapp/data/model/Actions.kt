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
}

sealed class FeatureFlagsTestAction : AppAction {
    object AddOneFeatureFlag : FeatureFlagsTestAction()

    object AddManyFeatureFlags : FeatureFlagsTestAction()
}

object ClearError : AppAction
