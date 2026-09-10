// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress(
    "EXPOSED_PARAMETER_TYPE",
    "EXPOSED_SUPER_INTERFACE",
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE"
)

package io.bitdrift.capture.test.support

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

object FakeBridge : IBridge {
    const val FAKE_LOGGER_ID = 1000L
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
        return FAKE_LOGGER_ID
    }

    override fun startLogger(loggerId: Long) = Unit

    override fun writeLog(
        loggerId: Long,
        logType: Int,
        logLevel: Int,
        log: String,
        fieldKeys: Array<String>?,
        fieldValues: Array<String>?,
        matchingFieldKeys: Array<String>?,
        matchingFieldValues: Array<String>?,
        usePreviousProcessSessionId: Boolean,
        overrideOccurredAtUnixMilliseconds: Long,
    ) = Unit
}
