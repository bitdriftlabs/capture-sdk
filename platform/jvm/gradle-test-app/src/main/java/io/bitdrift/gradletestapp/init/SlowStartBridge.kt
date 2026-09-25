// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.gradletestapp.init

import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.IBridge
import io.bitdrift.capture.ICustomFieldsProvider
import io.bitdrift.capture.IEventsListenerTarget
import io.bitdrift.capture.IPreferences
import io.bitdrift.capture.IResourceUtilizationTarget
import io.bitdrift.capture.ISessionReplayTarget
import io.bitdrift.capture.ITimestampProvider
import io.bitdrift.capture.error.IErrorReporter
import io.bitdrift.capture.network.ICaptureNetwork
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.session.SessionCallback
import io.bitdrift.capture.reports.IssueCallbackConfiguration

/**
 * A simulated slow native bridge to ease testing/verification of the recently
 * added PreInitMemoryBuffer
 */
internal class SlowStartBridge(
    private val delayMillis: Long,
) : IBridge {

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

        // Just to simulate a super expensive call on the caller thread while creating the logger
        Thread.sleep(delayMillis)

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
