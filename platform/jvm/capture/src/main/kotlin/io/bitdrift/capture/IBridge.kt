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
