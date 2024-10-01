// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge

final class LoggerBridgingFactory: LoggerBridgingFactoryProvider {
    func makeLogger(
        apiKey: String,
        bufferDirectoryPath: String?,
        sessionStrategy: SessionStrategy,
        metadataProvider: CaptureLoggerBridge.MetadataProvider,
        resourceUtilizationTarget: CaptureLoggerBridge.ResourceUtilizationTarget,
        eventsListenerTarget: CaptureLoggerBridge.EventsListenerTarget,
        appID: String,
        releaseVersion: String,
        network: Network?,
        errorReporting: RemoteErrorReporting
    ) -> LoggerBridging? {
        return LoggerBridge(
            apiKey: apiKey,
            bufferDirectoryPath: bufferDirectoryPath,
            sessionStrategy: sessionStrategy,
            metadataProvider: metadataProvider,
            resourceUtilizationTarget: resourceUtilizationTarget,
            eventsListenerTarget: eventsListenerTarget,
            appID: appID,
            releaseVersion: releaseVersion,
            network: network,
            errorReporting: errorReporting
        )
    }
}
