// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.gradletestapp.init

import android.content.Context
import io.bitdrift.capture.Capture
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.IBridge
import io.bitdrift.capture.ICustomFieldsProvider
import io.bitdrift.capture.IEventsListenerTarget
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.IPreferences
import io.bitdrift.capture.IResourceUtilizationTarget
import io.bitdrift.capture.ISessionReplayTarget
import io.bitdrift.capture.ITimestampProvider
import io.bitdrift.capture.error.IErrorReporter
import io.bitdrift.capture.network.ICaptureNetwork
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.FieldGetter
import io.bitdrift.capture.providers.Fields
import io.bitdrift.capture.providers.session.SessionCallback
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.IssueCallbackConfiguration
import okhttp3.HttpUrl

/**
 * QA-only wrapper around the real native bridge that sleeps before handing off to it, so a
 * settings toggle can simulate a slow internal SDK start and let testers manually exercise the
 * SDK's "still starting" window (pre-init buffering, etc). Never used outside that toggle.
 *
 * [IBridge], [CaptureJniLibrary], and the internal `Capture.Logger.start` overload used below are
 * all `internal` to the capture module; this file reaches into them the same way
 * FatalIssueGenerator/StrictModeConfigurator already do elsewhere in this app, via
 * `@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")`. Since this fully implements the real
 * IBridge interface, any future signature change to it will fail this file's compile loudly
 * rather than silently drifting.
 */
internal object SlowStartBridge : IBridge {

    override fun createLogger(
        sdkDirectory: String,
        apiKey: String,
        targetDomain: String,
        initialSessionId: String?,
        inactivityTimeoutMilliseconds: Long,
        sessionCallback: SessionCallback?,
        timestampProvider: ITimestampProvider?,
        customFieldsProvider: ICustomFieldsProvider?,
        initialOotbFields: Array<Field>,
        resourceUtilizationTarget: IResourceUtilizationTarget,
        sessionReplayTarget: ISessionReplayTarget,
        eventsListenerTarget: IEventsListenerTarget,
        applicationId: String,
        applicationVersion: String,
        osVersion: String,
        manufacturer: String,
        model: String,
        appVersionCode: Long,
        osApiLevel: Int,
        architecture: String,
        network: ICaptureNetwork,
        preferences: IPreferences,
        errorReporter: IErrorReporter,
        startInSleepMode: Boolean,
        issueCallbackConfiguration: IssueCallbackConfiguration?,
        initialFields: Array<Field>,
    ): Long {

        // Induce an artificial to ease E2E testing of PreInit memory buffer
        Thread.sleep(5000)

        return CaptureJniLibrary.createLogger(
            sdkDirectory = sdkDirectory,
            apiKey = apiKey,
            targetDomain = targetDomain,
            initialSessionId = initialSessionId,
            inactivityTimeoutMilliseconds = inactivityTimeoutMilliseconds,
            sessionCallback = sessionCallback,
            timestampProvider = timestampProvider,
            customFieldsProvider = customFieldsProvider,
            initialOotbFields = initialOotbFields,
            resourceUtilizationTarget = resourceUtilizationTarget,
            sessionReplayTarget = sessionReplayTarget,
            eventsListenerTarget = eventsListenerTarget,
            applicationId = applicationId,
            applicationVersion = applicationVersion,
            osVersion = osVersion,
            manufacturer = manufacturer,
            model = model,
            appVersionCode = appVersionCode,
            osApiLevel = osApiLevel,
            architecture = architecture,
            network = network,
            preferences = preferences,
            errorReporter = errorReporter,
            startInSleepMode = startInSleepMode,
            issueCallbackConfiguration = issueCallbackConfiguration,
            initialFields = initialFields,
        )
    }
}

/**
 * Starts the SDK through [SlowStartBridge], via the internal `bridge`-accepting overload of
 * `Capture.Logger.start`. Isolated here so the "reach into internals" surface stays in this one
 * file instead of spreading into [CaptureSdkInitializer].
 */
internal fun startCaptureSdkWithSimulatedDelay(
    apiKey: String,
    apiUrl: HttpUrl,
    configuration: Configuration,
    sessionStrategy: SessionStrategy,
    initialFields: Fields,
    context: Context,
    startResult: ((CaptureResult<ILogger>) -> Unit)?,
) {
    Capture.Logger.start(
        apiKey = apiKey,
        sessionStrategy = sessionStrategy,
        configuration = configuration,
        customFieldGetters = emptyList<FieldGetter>(),
        dateProvider = null,
        apiUrl = apiUrl,
        bridge = SlowStartBridge,
        context = context,
        initialFields = initialFields,
        startResult = startResult,
    )
}
