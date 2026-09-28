// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

/// Exports OTel spans for traced network requests to a user-configured OTLP/HTTP endpoint.
///
/// Uses its own small ephemeral `URLSession` (with no delegate, mirroring `APIClient`) rather
/// than the shared instrumented session, since it must never itself be traced or tracked --
/// see `URLSessionTracePropagation.bitdriftInternalTelemetryHeader`.
///
/// Export is best-effort: one POST attempt per span, dropped silently on any failure (including
/// the device being offline). A span is only useful while its trace is still assemblable by the
/// observability backend, so there is nothing to gain -- and real cost in complexity and device
/// resources -- from queuing or retrying a failed export. See
/// `docs/agent-tasks/otel-span-export-plan.md` for the full design rationale.
final class OtelSpanExporter {
    private let configuration: OtelExportConfiguration
    private let clientAttributes: ClientAttributes
    private let deviceAttributes: DeviceAttributes
    private let deviceIDProvider: () -> String
    private let sessionIDProvider: () -> String

    private lazy var session: URLSession = {
        let sessionConfiguration = URLSessionConfiguration.ephemeral
        sessionConfiguration.timeoutIntervalForRequest = 10.0
        return URLSession(configuration: sessionConfiguration, delegate: nil, delegateQueue: nil)
    }()

    init(
        configuration: OtelExportConfiguration,
        clientAttributes: ClientAttributes,
        deviceAttributes: DeviceAttributes,
        deviceIDProvider: @escaping () -> String,
        sessionIDProvider: @escaping () -> String
    ) {
        self.configuration = configuration
        self.clientAttributes = clientAttributes
        self.deviceAttributes = deviceAttributes
        self.deviceIDProvider = deviceIDProvider
        self.sessionIDProvider = sessionIDProvider
    }

    func export(_ span: OtelSpan) {
        let resource = OtelResourceAttributes.build(
            clientAttributes: self.clientAttributes,
            deviceAttributes: self.deviceAttributes,
            deviceID: self.deviceIDProvider(),
            sessionID: self.sessionIDProvider()
        )

        let payload = capture_build_otel_span_payload(
            span.traceID,
            span.spanID,
            OtelResourceAttributes.scopeName,
            span.name,
            span.startTimeUnixNano,
            span.endTimeUnixNano,
            span.isError ? 2 : 1, // OTLP Status.StatusCode: 1 = Ok, 2 = Error.
            "",
            span.attributes.encoded,
            resource.encoded
        )

        guard !payload.isEmpty, let body = payload.data(using: .utf8) else {
            return
        }

        var request = URLRequest(url: self.configuration.endpoint)
        request.httpMethod = "POST"
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(
            self.configuration.authHeaderValue,
            forHTTPHeaderField: self.configuration.authHeaderName
        )
        request.setValue(
            "true",
            forHTTPHeaderField: URLSessionTracePropagation.bitdriftInternalTelemetryHeader
        )

        self.session.dataTask(with: request) { _, _, _ in
            // Best-effort: failures are not retried, and are not surfaced to the host app, since a
            // dropped span must never affect the traced request itself.
        }.resume()
    }
}
