// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.

import Foundation

/// Configures the Capture session lifecycle.
///
/// This is the canonical session API. `SessionStrategy` is retained only as a compatibility shim.
/// Capture creates a session when the SDK starts and whenever `startNewSession` is called. An
/// SDK-created session ID is a UUID. A supplied ID is used exactly for the initialization or
/// explicit session start in which it is provided; it does not establish an ownership mode for
/// future sessions.
///
/// When `inactivityTimeout` is set, a period of inactivity rotates the session to an SDK-created
/// UUID. Calling `startNewSession(sessionID:)` with a non-`nil` ID always uses that ID, including
/// with an inactivity timeout configured. Mixing supplied IDs with SDK-created IDs is therefore
/// discouraged unless the application can handle both forms.
///
/// `onSessionIDChanged` is called after Capture durably updates the session ID for the initial
/// session, inactivity-driven rotations, and every explicit session start. It is always called
/// asynchronously on the main queue.
public struct SessionConfiguration {
    /// Optional ID to use for the initial session. When `nil`, Capture generates a UUID.
    public let initialSessionID: String?
    /// Optional inactivity duration after which Capture generates a new UUID session ID. When
    /// `nil`, activity-based rotation is disabled.
    public let inactivityTimeout: TimeInterval?
    /// Optional callback that receives each new session ID.
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

    func makeSessionConfigurationProvider() -> SessionConfigurationProvider {
        SessionConfigurationProvider(configuration: self)
    }
}

final class SessionConfigurationProvider: NSObject {
    private let configuration: SessionConfiguration

    init(configuration: SessionConfiguration) {
        self.configuration = configuration
    }

    @objc func initialSessionID() -> String? {
        configuration.initialSessionID
    }

    /// A negative value is the Objective-C bridge representation of an absent timeout.
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
