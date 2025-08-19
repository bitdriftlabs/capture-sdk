// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension URLSession {
    /// A safe drop-in replacement for the `URLSession.init(configuration:delegate:delegateQueue:)`
    /// initializer that integrates with the Capture SDK. This method makes minimal modifications to the
    /// passed arguments before passing them to the underlying
    /// `URLSession.init(configuration:delegate:delegateQueue:)` initializer and doesn't affect the actual
    /// execution of network requests.
    ///
    /// Refer to the official documentation for `URLSession.init(configuration:delegate:delegateQueue:)`
    /// to learn more about the method's parameters.
    ///
    /// - parameter configuration: The `URLSession` configuration to use.
    /// - parameter delegate:      The delegate for `URLSession`. The provided delegate is wrapped with
    ///                            a Capture proxy `URLSessionDelegate`, allowing the SDK to
    ///                            intercept certain delegate calls. All delegate callbacks, whether
    ///                            intercepted by the SDK or not, are delivered to the originally passed
    ///                            delegate instance.
    /// - parameter delegateQueue: An operation queue for scheduling the delegate calls and completion
    ///                            handlers.
    public convenience init(
        instrumentedSessionWithConfiguration configuration: URLSessionConfiguration,
        delegate: URLSessionDelegate? = nil,
        delegateQueue: OperationQueue? = nil
    ) {
        let newDelegate: URLSessionDelegate?
        if delegate?.isKind(of: ProxyURLSessionDelegate.self) == true {
            newDelegate = delegate
        } else {
            newDelegate = ProxyURLSessionDelegate(target: delegate)
        }

        if var protocolClasses = configuration.protocolClasses {
            protocolClasses.insert(CaptureURLProtocol.self, at: 0)
            configuration.protocolClasses = protocolClasses
        } else {
            configuration.protocolClasses = [CaptureURLProtocol.self]
        }

        // Depending on when the method is called the line below may called our swizzled `URLSession`
        // initializer or no.
        self.init(
            configuration: configuration,
            delegate: newDelegate,
            delegateQueue: delegateQueue
        )
    }
}
