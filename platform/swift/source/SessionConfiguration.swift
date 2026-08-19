// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Configures the Capture session lifecycle.
///
/// This is the canonical session API. `SessionStrategy` is retained only as a compatibility shim.
/// Capture creates a session when the SDK starts and whenever `startNewSession` is called. An
/// SDK-created session ID is a UUID. Without `inactivityTimeout`, Capture uses
/// `initialSessionID` whenever the SDK starts and never reuses a persisted session. With an
/// inactivity timeout, `initialSessionID` seeds only the first session; later SDK starts reuse
/// the persisted session while it remains active and create an SDK UUID only after the inactivity
/// period has elapsed.
///
/// When `inactivityTimeout` is set, a period of inactivity rotates the session to an SDK-created
/// UUID. Calling `startNewSession(sessionID:)` with a non-empty ID always uses that ID, including
/// with an inactivity timeout configured. Mixing supplied IDs with SDK-created IDs is therefore
/// discouraged unless the application can handle both forms.
/// Empty initial and explicit IDs are treated as absent, so Capture generates a UUID instead.
///
/// `onSessionIDChanged` is called after Capture starts the initial session, rotates after
/// inactivity, and on every explicit session start. An explicit start invokes the callback even
/// when it supplies the current session ID. It is always called asynchronously on the main queue.
/// When session starts overlap, callbacks can arrive in a different order from their state
/// transitions. Treat the callback ID as belonging to that individual start; use the logger's
/// `sessionID` property when the current session ID is required.
public struct SessionConfiguration {
    /// Optional non-empty ID to use whenever no inactivity timeout is configured, or to seed the
    /// first session when one is configured. When `nil` or empty, Capture generates a UUID.
    public let initialSessionID: String?
    /// Optional inactivity duration after which Capture generates a new UUID session ID. When
    /// `nil`, activity-based rotation is disabled.
    public let inactivityTimeout: TimeInterval?
    /// Optional callback that receives the active session ID after each session start or rotation,
    /// including explicit starts with the current ID. Calls from overlapping session starts are
    /// not guaranteed to arrive in transition order.
    public let onSessionIDChanged: ((String) -> Void)?

    public init(
        initialSessionID: String? = nil,
        inactivityTimeout: TimeInterval? = nil,
        onSessionIDChanged: ((String) -> Void)? = nil
    ) {
        self.initialSessionID = initialSessionID
        self.inactivityTimeout = inactivityTimeout
        self.onSessionIDChanged = onSessionIDChanged
    }

    func makeSessionConfigurationBridge() -> SessionConfigurationBridge {
        SessionConfigurationBridge(configuration: self)
    }
}

final class SessionConfigurationBridge: NSObject {
    private let configuration: SessionConfiguration

    init(configuration: SessionConfiguration) {
        self.configuration = configuration
    }

    @objc func initialSessionID() -> String? {
        configuration.initialSessionID
    }

    /// - returns: A negative value when the Objective-C bridge should represent an absent timeout.
    @objc func inactivityTimeoutSeconds() -> Double {
        configuration.inactivityTimeout ?? -1
    }

    @objc func sessionIDChanged(_ sessionID: String) {
        DispatchQueue.main.async {
            self.configuration.onSessionIDChanged?(sessionID)
        }
    }
}

/// Objective-C wrapper for the canonical session configuration API.
///
/// This has the same session-ID lifecycle and callback guarantees as `SessionConfiguration`.
@objc(CAPSessionConfiguration)
public final class SessionConfigurationObjc: NSObject {
    let underlyingConfiguration: SessionConfiguration

    @objc public init(
        initialSessionID: String? = nil,
        inactivityTimeoutSeconds: NSNumber? = nil,
        onSessionIDChanged: ((String) -> Void)? = nil
    ) {
        self.underlyingConfiguration = SessionConfiguration(
            initialSessionID: initialSessionID,
            inactivityTimeout: inactivityTimeoutSeconds?.doubleValue,
            onSessionIDChanged: onSessionIDChanged
        )
    }
}
