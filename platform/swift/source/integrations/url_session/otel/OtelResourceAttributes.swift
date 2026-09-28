// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

/// Builds the OTLP `Resource` attributes describing the app/device/SDK instance emitting spans.
/// Mirrors the Android implementation (`OtelResourceAttributes.kt`) field for field.
///
/// `bitdrift.session_id` is our own namespace, not an OTel semantic convention key -- every other
/// attribute here maps to a real key from https://opentelemetry.io/docs/specs/semconv/.
enum OtelResourceAttributes {
    /// The instrumentation scope name reported alongside every span.
    static let scopeName = "io.bitdrift.capture-ios"

    private static let sdkName = "io.bitdrift.capture-ios"

    static func build(
        clientAttributes: ClientAttributes,
        deviceAttributes: DeviceAttributes,
        deviceID: String,
        sessionID: String
    ) -> OtelAttributes
    {
        let attributes = OtelAttributes()
        attributes.add("service.name", clientAttributes.appID)
        attributes.add("service.version", clientAttributes.appVersion)
        attributes.add("device.id", deviceID)
        attributes.add("device.model.name", deviceAttributes.hardwareVersion)
        attributes.add("device.manufacturer", "Apple")
        attributes.add("os.version", clientAttributes.osVersion)
        attributes.add("telemetry.sdk.name", self.sdkName)
        attributes.add("telemetry.sdk.version", capture_get_sdk_version())
        attributes.add("bitdrift.session_id", sessionID)
        return attributes
    }
}
