// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// A Capture SDK logger integration. Each integration provides logs for a specific family of APIs
/// (e.g., URL APIs) or a third-party library (e.g., `CocoaLumberjack`).
public final class Integration {
    private var isStarted = false
    let start: (_ logger: Logging, _ disableSwizzling: Bool, _ requestFieldProvider: URLSessionRequestFieldProvider?) -> Void

    /// Creates a new integration.
    ///
    /// - parameter start: A closure that is called by the Capture SDK to notify the receiver that a given
    ///                    integration should start. The `Logging` instance passed as an argument to the
    ///                    closure should be used by the integration to emit Capture SDK logs.
    public init(start: @escaping (_ logger: Logging, _ disableSwizzling: Bool, _ requestFieldProvider: URLSessionRequestFieldProvider?) -> Void) {
        self.start = start
    }

    /// Starts the integration.
    ///
    /// - parameter logger:               The logger instance that should be used by the integration being
    ///                                   started to emit Capture SDK logs.
    /// - parameter disableSwizzling:     Whether the integration is allowed to do swizzling.
    /// - parameter requestFieldProvider: Provider for extra fields appended to HTTP requests.
    public func start(
        with logger: Logging,
        disableSwizzling: Bool = false,
        requestFieldProvider: URLSessionRequestFieldProvider? = nil
    ) {
        if self.isStarted {
            // TODO(Augustyniak): Log something here.
            return
        }

        self.isStarted = true
        self.start(logger, disableSwizzling, requestFieldProvider)
    }
}

/// An integrator that can be used to enable various SDK integrations.
public final class LoggerIntegrator {
    private var enabled = false
    let logger: Logging

    init(logger: Logging) {
        self.logger = logger
    }

    /// Enables the given integrations. This method should be called at most once; subsequent calls will have
    /// no effect.
    ///
    /// - important: The SDK doesn't do any swizzling unless the `Integration.urlSession` integration is
    ///              enabled.
    ///
    /// - parameter integrations:         The list of integrations to enable.
    /// - parameter disableSwizzling:     Whether integrations is allowed to do swizzling.
    /// - parameter requestFieldProvider: Provider for extra fields appended to HTTP requests.
    ///
    /// - returns: The configured logger. This is the same instance that can be accessed via the
    ///            `Logger.shared` property, but it's returned in a non-optional form here.
    @discardableResult
    public func enableIntegrations(
        _ integrations: [Integration],
        disableSwizzling: Bool = false,
        requestFieldProvider: URLSessionRequestFieldProvider?=nil
    ) -> Logging {
        if self.enabled {
            return self.logger
        }

        self.enabled = true
        for integration in integrations {
            integration.start(self.logger, disableSwizzling, requestFieldProvider)
        }

        return self.logger
    }
}
