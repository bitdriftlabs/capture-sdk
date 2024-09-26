// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension URLSession {
    // Accessing `cap_underlyingShared` is be thread-safe.
    // Apple docs about type (static) properties:
    //   > Stored type properties are lazily initialized on their first access. They’re guaranteed
    //   > to be initialized only once, even when accessed by multiple threads simultaneously, and
    //   > they don’t need to be marked with the lazy modifier.
    // swiftlint:disable:next identifier_name
    private static var cap_underlyingShared: URLSession = {
        // swiftlint:disable line_length
        // https://github.com/apple/swift-corelibs-foundation/blob/44c71375856b0472063a7a08e52734672ee60851/Sources/FoundationNetworking/URLSession/URLSession.swift#L210-L219
        let configuration: URLSessionConfiguration = .default
        configuration.httpCookieStorage = .shared
        return URLSession(configuration: .default, delegate: nil, delegateQueue: nil)
    }()

    @objc
    class func cap_makeSession(
        configuration: URLSessionConfiguration,
        delegate: URLSessionDelegate?,
        delegateQueue: OperationQueue?
    ) -> URLSession {
        // The call below doesn't result in an infinite cycle as the implementation of
        // `cap_make(configuration:delegate:delegateQueue)` was used to replace the implementation of
        // `URLSession.init(configuration:delegate:delegateQueue)` so the call below calls the original
        // initializer.

        if let delegate {
            // Proxying delegates of some 3rd party frameworks leads to crashes. Disable proxying for
            // problematic classes.
            // Refer to the following GitHub comments for more details:
            //  * https://github.com/google/gtm-session-fetcher/issues/190#issuecomment-604205556
            //  * https://github.com/google/gtm-session-fetcher/issues/190#issuecomment-604757154
            let disabledDelegateClassNames = [
                // GooglePlaces SDK
                "GMPx_GTMSessionFetcherService",
                "GMPx_GTMSessionFetcherSessionDelegateDispatcher",
                // GoogleMaps SDK
                "GMSx_GTMSessionFetcherService",
                "GMSx_GTMSessionFetcherSessionDelegateDispatcher",
                // GTMSessionFetcher SDK
                "GTMSessionFetcherService",
                "GTMSessionFetcherSessionDelegateDispatcher",
            ]

            let shouldDisableProxying = disabledDelegateClassNames
                .compactMap { NSClassFromString($0) }
                .contains { delegate.isKind(of: $0) }

            if shouldDisableProxying {
                return Self.cap_makeSession(
                    configuration: configuration,
                    delegate: delegate,
                    delegateQueue: delegateQueue
                )
            }
        }

        let newDelegate: URLSessionDelegate?
        if delegate?.isKind(of: ProxyURLSessionDelegate.self) == true {
            newDelegate = delegate
        } else {
            newDelegate = ProxyURLSessionDelegate(target: delegate as? URLSessionTaskDelegate)
        }

        if var protocolClasses = configuration.protocolClasses {
            if !protocolClasses.contains(where: { $0 == CaptureURLProtocol.self }) {
                protocolClasses.insert(CaptureURLProtocol.self, at: 0)
                configuration.protocolClasses = protocolClasses
            }
        } else {
            configuration.protocolClasses = [CaptureURLProtocol.self]
        }

        return Self.cap_makeSession(
            configuration: configuration,
            delegate: newDelegate,
            delegateQueue: delegateQueue
        )
    }

    @objc
    class func cap_shared() -> URLSession {
        return self.cap_underlyingShared
    }
}
