// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import Network

private enum ReachabilityPath: String {
    case wlan
    case wwan
    case ethernet
    case other

    init(from path: NWPath) {
        if path.usesInterfaceType(.wifi) {
            self = .wlan
        } else if path.usesInterfaceType(.wiredEthernet) {
            self = .ethernet
        } else if path.usesInterfaceType(.cellular) {
            self = .wwan
        } else {
            self = .other
        }
    }
}

/// Attributes related to the network conditions, including active interfaces metadata, cellular
/// information and the like.
final class NetworkAttributes {
    private let pathMonitor: NWPathMonitor
    private let queue = DispatchQueue.serial(withLabelSuffix: "NetworkAttributes", target: .default)
    private var reachabilityPath: Atomic<ReachabilityPath> = Atomic(.other)
    private weak var logger: CoreLogging?

    // This property is static as static ('type') properties are guaranteed to be initialized in a lazily
    // manner only once even when accessed by multiple threads at a time when property's value has not been
    // initialized yet. For type properties such guarantee does not exist and their values may be initialized
    // multiple times if multiple threads access them at a time when their value is not initialized yet.
    //
    // We want the `TelephonyNetworkInfo` to be initialized on the first access of its value as that first
    // access happens on Rust background thread and that makes `Logger.configure(...)` method call take
    // significantly less time.
    //
    // See the following quotes from Apple docs available at
    // https://docs.swift.org/swift-book/documentation/the-swift-programming-language/properties/
    //
    // Apple docs about type (static) properties:
    //   > Stored type properties are lazily initialized on their first access. They’re guaranteed
    //   > to be initialized only once, even when accessed by multiple threads simultaneously, and
    //   > they don’t need to be marked with the lazy modifier.
    //
    // Apple docs about instance (not static) properties:
    //   > If a property marked with the lazy modifier is accessed by multiple threads simultaneously
    //   > and the property hasn’t yet been initialized, there’s no guarantee that the property will
    //   > be initialized only once.
    private static var telephonyNetworkInfo = TelephonyNetworkInfo()

    init() {
        self.pathMonitor = NWPathMonitor()
        self.pathMonitor.pathUpdateHandler = { [weak self] path in
            guard let self else {
                return
            }

            let reachability = ReachabilityPath(from: path)
            self.reachabilityPath.update { $0 = reachability }

            // The initial path update is called before a shared logger is configured so we cannot assert on
            // the existence of the logger in here.
            self.logger?.log(
                level: .debug,
                message: "[NetworkAttributes] Reachability updated: \(reachability)",
                type: .internalsdk
            )
        }
    }

    func start(with logger: CoreLogging) {
        guard self.logger == nil else {
            return
        }

        self.logger = logger
        self.pathMonitor.start(queue: self.queue)
    }

    deinit {
        self.pathMonitor.cancel()
    }
}

extension NetworkAttributes: FieldProvider {
    public func getFields() -> Fields {
        return [
            /// The cellular network type. Note a given network can support multiple paths. We'll
            /// report the currently used. (e.g. wlan)
            "network_type": self.reachabilityPath.load().rawValue,

            /// Cellular access technology that is active at the moment (eg. LTE)
            "radio_type": Self.telephonyNetworkInfo.radioType.load() ?? "unknown",
        ]
    }
}
