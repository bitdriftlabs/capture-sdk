// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.error.IErrorReporter
import io.bitdrift.capture.network.ICaptureNetwork
import io.bitdrift.capture.providers.session.SessionStrategyConfiguration

internal interface IBridge {
    fun createLogger(
        sdkDirectory: String,
        apiKey: String,
        sessionStrategy: SessionStrategyConfiguration,
        metadataProvider: IMetadataProvider,
        resourceUtilizationTarget: IResourceUtilizationTarget,
        eventsListenerTarget: IEventsListenerTarget,
        applicationId: String,
        applicationVersion: String,
        network: ICaptureNetwork,
        preferences: IPreferences,
        errorReporter: IErrorReporter,
    ): Long
}
