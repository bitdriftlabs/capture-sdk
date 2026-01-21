// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.webview.WebViewConfiguration

/**
 * A configuration object representing the feature set enabled for Capture.
 * @param sessionReplayConfiguration The resource reporting configuration to use. Passing `null` disables the feature.
 * @param enableFatalIssueReporting When set to true wil capture Fatal Issues automatically [JVM crash, ANR, etc] and without requiring
 * any external 3rd party library integration
 * @param sleepMode SleepMode.ENABLED if Capture should initialize in minimal activity mode
 * @param webViewConfiguration The WebView instrumentation configuration. Requires the `io.bitdrift.capture-plugin`
 *                             Gradle plugin with `automaticWebViewInstrumentation = true` enabled.
 *                             Passing `null` disables WebView monitoring.
*/
data class Configuration
    @JvmOverloads
    constructor(
        val sessionReplayConfiguration: SessionReplayConfiguration? = SessionReplayConfiguration(),
        val enableFatalIssueReporting: Boolean = true,
        val sleepMode: SleepMode = SleepMode.DISABLED,
        @property:ExperimentalBitdriftApi
        val webViewConfiguration: WebViewConfiguration? = null,
    )
