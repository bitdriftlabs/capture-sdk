// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp.otel

import io.bitdrift.capture.ILogger
import io.bitdrift.capture.attributes.ClientAttributes

/**
 * Builds the OTLP `Resource` block describing the app/device/SDK instance emitting spans.
 *
 * `bitdrift.session_id` is our own namespace, not an OTel semantic convention key — every other
 * attribute here maps to a real key from https://opentelemetry.io/docs/specs/semconv/.
 */
internal object OtelResourceAttributes {
    fun build(
        clientAttributes: ClientAttributes,
        logger: ILogger,
        sdkVersion: String,
    ): Resource =
        Resource(
            attributes =
                listOf(
                    KeyValue.of("service.name", clientAttributes.appId),
                    KeyValue.of("service.version", clientAttributes.appVersion),
                    KeyValue.of("device.id", logger.deviceId),
                    KeyValue.of("device.model.name", clientAttributes.model),
                    KeyValue.of("device.manufacturer", clientAttributes.manufacturer),
                    KeyValue.of("os.version", clientAttributes.osVersion),
                    KeyValue.of("telemetry.sdk.name", ClientAttributes.SDK_LIBRARY_ID),
                    KeyValue.of("telemetry.sdk.version", sdkVersion),
                    KeyValue.of("bitdrift.session_id", logger.sessionId),
                ),
        )
}
