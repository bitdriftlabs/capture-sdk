// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
internal import CapturePassable

final class LoggerBridgingFactory: LoggerBridgingFactoryProvider {
    func makeLogger(
        apiKey: String,
        bufferDirectoryPath: String,
        sessionStrategy: SessionStrategy,
        metadataProvider: CaptureLoggerBridge.MetadataProvider,
        initialOotbFields: [CapturePassable.Field],
        resourceUtilizationTarget: CaptureLoggerBridge.ResourceUtilizationTarget,
        sessionReplayTarget: CaptureLoggerBridge.SessionReplayTarget,
        eventsListenerTarget: CaptureLoggerBridge.EventsListenerTarget,
        appID: String,
        releaseVersion: String,
        buildNumber: String,
        osVersion: String,
        model: String,
        network: Network?,
        errorReporting: RemoteErrorReporting,
        sleepMode: SleepMode,
        issueCallbackConfiguration: IssueCallbackConfiguration?
    ) -> LoggerBridging? {
        return LoggerBridge(
            apiKey: apiKey,
            bufferDirectoryPath: bufferDirectoryPath,
            sessionStrategy: sessionStrategy,
            metadataProvider: metadataProvider,
            initialOotbFields: initialOotbFields,
            resourceUtilizationTarget: resourceUtilizationTarget,
            sessionReplayTarget: sessionReplayTarget,
            eventsListenerTarget: eventsListenerTarget,
            appID: appID,
            releaseVersion: releaseVersion,
            buildNumber: buildNumber,
            osVersion: osVersion,
            model: model,
            network: network,
            errorReporting: errorReporting,
            sleepMode: sleepMode,
            issueCallbackConfiguration: issueCallbackConfiguration
        )
    }
}
