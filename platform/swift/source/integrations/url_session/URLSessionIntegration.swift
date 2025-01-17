// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

// Ensures that the hook installation process happens at most once.
private let kSwizzleOnce: () = URLSessionIntegration.enableNetworkInstrumentationOnce()
// The OS logger to use by the library.
let kLogger = OSLogger(subsystem: "Capture.URLSessionIntegration")

extension Integration {
    /// The integration that emits logs for tasks started using `URLSession` instances.
    /// It is compatible with all types of tasks except `URLSessionWebSocketTask` and `URLSessionStreamTask`.
    /// For best results, start the integration before initializing any instance of `URLSession` in your app.
    ///
    /// For a seamless integration experience that works with all `URLSession` instances — including
    /// `URLSession.shared` — without requiring extra work on your part, allow the SDK to perform swizzling by
    /// calling `enableIntegrations(_:disableSwizzling:)` with `disableSwizzling` set to `false`. Otherwise,
    /// use the `URLSession.capture_makeSession(configuration:delegate:delegateQueue:)` method to create
    /// `URLSession` instances in your app.
    ///
    /// - important: If the integration is started after the app has created or accessed instances of
    ///              `URLSession`, it may result in the SDK not emitting logs for `URLSessionTask`s started
    ///              with those sessions. To ensure that the SDK emits logs for all network requests, start
    ///              the integration as early in the app's lifecycle as possible, or use the
    ///              `URLSession.capture_makeSession(configuration:delegate:delegateQueue:)` method to create
    ///              `URLSession` instances.
    ///
    /// - returns: The `URLSession` integration.
    public static func urlSession() -> Integration {
        .init { logger, disableSwizzling in
            URLSessionIntegration.shared.start(
                logger: logger,
                disableSwizzling: disableSwizzling
            )
        }
    }
}

final class URLSessionIntegration {
    /// The instance of Capture logger the library should use for logging.
    private let underlyingLogger = Atomic<Logging?>(nil)
    static let shared = URLSessionIntegration()

    var logger: Logging? {
        return self.underlyingLogger.load()
    }

    func start(logger: Logging, disableSwizzling: Bool) {
        self.underlyingLogger.update { $0 = logger }
        if !disableSwizzling {
            kSwizzleOnce
        }
    }

    // MARK: - Private

    fileprivate static func enableNetworkInstrumentationOnce() {
        self.installURLSessionDelegateHook()
    }

    private static func installURLSessionDelegateHook() {
        if #available(iOS 15.0, *) {
            let URLSessionTaskInternalClass: AnyClass = self.getTaskClass()
            exchangeInstanceMethod(
                class: URLSessionTaskInternalClass,
                selector: #selector(URLSessionTask.resume),
                with: #selector(URLSessionTask.cap_resume)
            )
        } else {
            Self.shared.logger?.log(level: .error,
                                    message: "Network Swizzling is not available in iOS < 15.0")
        }
    }

    private static func getTaskClass() -> AnyClass {
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        let request = URLRequest(url: URL(string: "www.bitdrift.io")!)
        let session = URLSession(configuration: .ephemeral)
        defer { session.invalidateAndCancel() }

        return type(of: session.dataTask(with: request))
    }
}
