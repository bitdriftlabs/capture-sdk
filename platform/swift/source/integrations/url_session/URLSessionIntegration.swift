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
        self.installHooks()
    }

    private static func installHooks() {
        // TODO(Augustyniak): Avoid swizzling if swizzling disabled.
        self.installURLSessionHooks()
        let klass: AnyClass = self.getTaskClass()
        self.installURLSessionDelegateHook(class: klass)
    }

    private static func installURLSessionHooks() {
        // Even though `URLSession.init(configuration:delegate:delegateQueue:)` appears to be a standard
        // initializer in Swift (an instance method), it's backed by the static
        // `[NSURLSession sessionWithConfiguration:delegate:delegateQueue:]` method. Therefore, we work
        // with it as if it were a static method that returns an instance of URLSession.
        exchangeClassMethod(
            class: URLSession.self,
            selector: #selector(URLSession.init(configuration:delegate:delegateQueue:)),
            with: #selector(URLSession.cap_makeSession(configuration:delegate:delegateQueue:))
        )

        // Unfortunately, swizzling `URLSession.init(configuration:delegate:delegateQueue:)` isn't enough to
        // swizzle the initializer used to create `URLSession.shared`. Therefore, we swizzle
        // `URLSession.shared` directly.
        exchangeClassMethod(
            class: URLSession.self,
            selector: #selector(getter: URLSession.shared),
            with: #selector(URLSession.cap_shared)
        )
    }

    private static func installURLSessionDelegateHook(class: AnyClass) {
        if #available(iOS 15.0, *) {
            exchangeInstanceMethod(
                class: `class`,
                selector: #selector(setter: URLSessionTask.delegate),
                with: #selector(URLSessionTask.cap_setDelegate)
            )
        }
    }

    private static func getTaskClass() -> AnyClass {
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        var request = URLRequest(url: URL(string: "www.bitdrift.io")!)
        request.setValue("true", forHTTPHeaderField: kCaptureAPIHeaderField)

        let session = URLSession(configuration: .ephemeral)
        defer { session.invalidateAndCancel() }

        return type(of: session.dataTask(with: request))
    }
}
