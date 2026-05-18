// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

/// The SDK's initialization state.
@objc(CAPInitializationState)
public enum InitializationState: Int {
    /// The SDK has not been started yet.
    case notStarted = 0
    /// The SDK library has been loaded and the logger is being constructed,
    /// but log processing has not yet begun.
    case loaded = 1
    /// The SDK is fully running and processing logs.
    case running = 2
    /// The SDK has been force-disabled by the server (e.g., authentication failure).
    case disabled = 3
}

/// A point-in-time snapshot of the SDK's operational status.
@objc(CAPSdkStatus)
public final class SdkStatus: NSObject {
    /// The current initialization state of the SDK.
    @objc public let initializationState: InitializationState

    /// The time of the last successful handshake with the backend, or nil if none yet.
    @objc public let lastHandshakeTime: Date?

    /// The time of the last successful config delivery, or nil if none yet.
    @objc public let lastConfigDeliveryTime: Date?

    init(initializationState: InitializationState, lastHandshakeTime: Date?, lastConfigDeliveryTime: Date?) {
        self.initializationState = initializationState
        self.lastHandshakeTime = lastHandshakeTime
        self.lastConfigDeliveryTime = lastConfigDeliveryTime
    }

    /// Creates an `SdkStatus` from the FFI struct returned by Rust.
    ///
    /// - parameter ffi: The C-compatible struct from the Rust bridge.
    ///
    /// - returns: A new `SdkStatus` instance.
    static func from(ffi: SdkStatusFFI) -> SdkStatus {
        let state = InitializationState(rawValue: Int(ffi.initialization_state)) ?? .notStarted

        let handshakeTime: Date? = ffi.last_handshake_time_ms >= 0
            ? Date(timeIntervalSince1970: Double(ffi.last_handshake_time_ms) / 1000.0)
            : nil

        let configDeliveryTime: Date? = ffi.last_config_delivery_time_ms >= 0
            ? Date(timeIntervalSince1970: Double(ffi.last_config_delivery_time_ms) / 1000.0)
            : nil

        return SdkStatus(
            initializationState: state,
            lastHandshakeTime: handshakeTime,
            lastConfigDeliveryTime: configDeliveryTime
        )
    }
}
