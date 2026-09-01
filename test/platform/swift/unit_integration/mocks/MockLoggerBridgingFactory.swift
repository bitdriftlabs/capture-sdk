// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureLoggerBridge
import CapturePassable
import Foundation

public final class MockLoggerBridgingFactory: LoggerBridgingFactoryProvider {
    private let logger: LoggerBridging?
    public private(set) var makeLoggerCallsCount = 0
    public private(set) var targetDomains: [String] = []
    public private(set) var initialOotbFields = [CapturePassable.Field]()

    public init(logger: LoggerBridging?) {
        self.logger = logger
    }

    public func makeLogger(
        apiKey _: String,
        bufferDirectoryPath _: String,
        sessionStrategy _: SessionStrategy,
        metadataProvider _: CaptureLoggerBridge.MetadataProvider,
        initialOotbFields: [CapturePassable.Field],
        resourceUtilizationTarget _: CaptureLoggerBridge.ResourceUtilizationTarget,
        sessionReplayTarget _: CaptureLoggerBridge.SessionReplayTarget,
        eventsListenerTarget _: CaptureLoggerBridge.EventsListenerTarget,
        appID _: String,
        releaseVersion _: String,
        buildNumber _: String,
        osVersion _: String,
        model _: String,
        targetDomain: String,
        network _: Network?,
        errorReporting _: RemoteErrorReporting,
        sleepMode _: Capture.SleepMode,
        initialFields _: [CapturePassable.Field],
        issueCallbackConfiguration _: IssueCallbackConfiguration?
    ) -> LoggerBridging? {
        self.makeLoggerCallsCount += 1
        self.targetDomains.append(targetDomain)
        self.initialOotbFields = initialOotbFields
        return self.logger
    }
}
