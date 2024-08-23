// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
@_implementationOnly import CapturePassable
import Foundation

/// Describes the strategy to use for session management.
public enum SessionStrategy {
    /// A session strategy that never expires the session ID but does not survive process restart.
    ///
    /// The initial session ID is retrieved by calling the passed closure.
    ///
    /// Whenever a new session is manually started via `startNewSession` method call, the closure is
    /// invoked to generate a new session ID.
    ///
    /// - parameter sessionIDGenerator: The closure that returns the session ID to use. Upon the
    ///                                 initialization of the logger the closure is called on the thread
    ///                                 that's used to configure the logger. Subsequent closure calls are
    ///                                 performed every time `Logger.startNewSession` method is called using
    ///                                 the thread on which the method is called.
    case fixed(sessionIDGenerator: (() -> String) = { UUID().uuidString })

    /// A session strategy that generates a new session ID after a certain period of app inactivity.
    ///
    /// The inactivity duration is measured by the minutes elapsed since the last log. The session ID is
    /// persisted to disk and survives app restarts.
    ///
    /// For this session strategy, each log emitted by the SDK — including those from session replay and
    /// resource monitoring feature — is considered an app activity.
    ///
    /// - parameter inactivityThresholdMins: The amount of minutes of inactivity after which a session ID
    ///                                      changes. The default value is 30 minutes.
    /// - parameter onSessionIDChanged:      Closure that is invoked with the new value every time the session
    ///                                      ID changes. This callback is dispatched asynchronously to the
    ///                                      main queue.
    case activityBased(inactivityThresholdMins: Int = 30, onSessionIDChanged: ((String) -> Void)? = nil)
}

extension SessionStrategy {
    func makeSessionStrategyProvider() -> SessionStrategyProvider {
        SessionStrategyConfiguration(sessionStrategy: self)
    }
}

final class SessionStrategyConfiguration: NSObject {
    private let underlyingSessionStrategy: SessionStrategy

    init(sessionStrategy: SessionStrategy) {
        self.underlyingSessionStrategy = sessionStrategy
        super.init()
    }
}

extension SessionStrategyConfiguration: SessionStrategyProvider {
    @objc
    func sessionIDChanged(_ sessionID: String) {
        switch self.underlyingSessionStrategy {
        case .fixed:
            assertionFailure("sessionChanged should not be called on fixed session strategy")
        case let .activityBased(_, onSessionChanged):
            DispatchQueue.main.async {
                onSessionChanged?(sessionID)
            }
        }
    }

    @objc
    func generateSessionID() -> String {
        switch self.underlyingSessionStrategy {
        case let .fixed(sessionIDGenerator):
            return sessionIDGenerator()
        case .activityBased:
            assertionFailure("generateSessionID should not be called on activityBased session strategy")
            return UUID().uuidString
        }
    }

    @objc
    func inactivityThresholdMins() -> Int {
        switch self.underlyingSessionStrategy {
        case .fixed:
            assertionFailure("inactivityThresholdMins should not be called on fixed session strategy")
            return 30
        case let .activityBased(inactivityThresholdMins, _):
            return inactivityThresholdMins
        }
    }

    @objc
    func sessionStrategyType() -> CapturePassable.SessionStrategyType {
        switch self.underlyingSessionStrategy {
        case .fixed:
            CapturePassable.SessionStrategyType.fixed
        case .activityBased:
            CapturePassable.SessionStrategyType.activityBased
        }
    }
}
