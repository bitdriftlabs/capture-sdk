// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureLoggerBridge
import Foundation

final class MockLoggerBridgingFactory: LoggerBridgingFactoryProvider {
    private let logger: LoggerBridging

    init(logger: LoggerBridging) {
        self.logger = logger
    }

    func makeLogger(
        apiKey _: String,
        bufferDirectoryPath _: String?,
        sessionStrategy _: SessionStrategy,
        metadataProvider _: CaptureLoggerBridge.MetadataProvider,
        resourceUtilizationTarget _: CaptureLoggerBridge.ResourceUtilizationTarget,
        eventsListenerTarget _: CaptureLoggerBridge.EventsListenerTarget,
        appID _: String,
        releaseVersion _: String,
        network _: Network?,
        errorReporting _: RemoteErrorReporting
    ) -> LoggerBridging {
        return self.logger
    }
}
