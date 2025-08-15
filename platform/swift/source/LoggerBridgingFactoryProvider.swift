// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge

/// Responsible for creating logger bridging.
protocol LoggerBridgingFactoryProvider {
    /// Creates a new instance of logger bridging.
    ///
    /// - parameter apiKey:                    The bitdrift Capture API key.
    /// - parameter bufferDirectoryPath:       The directory to use for storing files.
    /// - parameter sessionStrategy:           The session strategy to use.
    /// - parameter metadataProvider:          The metadata provider to use.
    /// - parameter resourceUtilizationTarget: The resource utilization target to use.
    /// - parameter sessionReplayTarget:       The session replay target to use.
    /// - parameter eventSubscriber:           The events listener target to use.
    /// - parameter appID:                     The host application application identifier.
    /// - parameter releaseVersion:            The host application release version.
    /// - parameter model:                     The host device model.
    /// - parameter network:                   The interface to use for network operations.
    /// - parameter errorReporting:            The interface to use for reporting errors.
    /// - parameter sleepMode:                 .active if sleep mode should be initialized now
    ///
    /// - returns: The logger bridging instance.
    func makeLogger(
        apiKey: String,
        bufferDirectoryPath: String?,
        sessionStrategy: SessionStrategy,
        metadataProvider: CaptureLoggerBridge.MetadataProvider,
        resourceUtilizationTarget: CaptureLoggerBridge.ResourceUtilizationTarget,
        sessionReplayTarget: CaptureLoggerBridge.SessionReplayTarget,
        eventSubscriber: CaptureLoggerBridge.EventSubscriber,
        appID: String,
        releaseVersion: String,
        model: String,
        network: Network?,
        errorReporting: RemoteErrorReporting,
        sleepMode: SleepMode
    ) -> LoggerBridging?
}
