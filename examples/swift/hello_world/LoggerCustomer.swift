// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import Foundation
import MetricKit

private let kDeviceId = "ios-helloworld"

private struct EncodableExampleStruct: Encodable {
    private struct InternalEncodableExampleStruct: Encodable {
        let stringField = "internal_foo"
    }

    let aStringField = "foo"
    let bFieldArray = ["first_value", "second_value"]
    let cFieldDictionary = [
        "key_1": "value_1",
        "key_2": "value_2",
    ]
    private let dFieldStruct = InternalEncodableExampleStruct()
}

final class LoggerCustomer: NSObject, URLSessionDelegate {
    private struct RequestDefinition {
        let method: String
        let url: URL
    }

    enum LogLevel: String, CaseIterable, Identifiable {
        case error
        case warning
        case info
        case debug
        case trace

        var id: String { return self.rawValue }
    }

    private var appStartTime: Date

    private let requestDefinitions = [
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        RequestDefinition(method: "GET", url: URL(string: "https://httpbin.org/get")!),
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        RequestDefinition(method: "POST", url: URL(string: "https://httpbin.org/post")!),
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        RequestDefinition(method: "GET", url: URL(string: "https://cat-fact.herokuapp.com/facts/random")!),
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        RequestDefinition(method: "GET", url: URL(string: "https://api.fisenko.net/v1/quotes/en/random")!),
        RequestDefinition(
            method: "GET",
            url: URL(
                // swiftlint:disable:next line_length use_static_string_url_init
                string: "https://api.census.gov/data/2021/pep/population?get=DENSITY_2021,NAME,STATE&for=state:36"
                // swiftlint:disable:next force_unwrapping
            )!
        ),
    ]

    private lazy var dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.timeStyle = .long
        return formatter
    }()

    var sessionID: String? {
        return Capture.Logger.sessionID
    }

    var sessionURL: String? {
        return Capture.Logger.sessionURL
    }

    override init() {
        self.appStartTime = Date()

        super.init()

        guard let apiURL = URL(string: Configuration.storedAPIURL) else {
            print("failed to initialize logger due to invalid API URL: \(Configuration.storedAPIURL)")
            return
        }

        Logger
            .start(
                withAPIKey: Configuration.storedAPIKey ?? "",
                sessionStrategy: .fixed(),
                configuration: Capture.Configuration(),
                fieldProviders: [CustomFieldProvider()],
                apiURL: apiURL
            )?
            .enableIntegrations(
                [.urlSession(requestFieldProvider: CustomNetworkFieldProvider())],
                disableSwizzling: true
            )

        Logger.addField(withKey: "field_container_field_key", value: "field_container_value")
        Logger.logInfo("App launched. Logger configured.")

        MXMetricManager.shared.add(self)
    }

    func startNewSession() {
        Logger.startNewSession()
    }

    func createTemporaryDeviceCode(completion: @escaping (Result<String, Error>) -> Void) {
        Logger.createTemporaryDeviceCode(completion: completion)
    }

    func setFeatureFlag(name: String, variant: String?) {
        Capture.Logger.setFeatureFlag(withName: name, variant: variant)
    }

    func setFeatureFlags(_ flags: [FeatureFlag]) {
        Capture.Logger.setFeatureFlags(flags)
    }

    func removeFeatureFlag(flag: String) {
        Capture.Logger.removeFeatureFlag(withName: flag)
    }

    func clearFeatureFlags() {
        Capture.Logger.clearFeatureFlags()
    }

    func performRandomNetworkRequestUsingDataTask() {
        let session = URLSession(
            instrumentedSessionWithConfiguration: .default,
            delegate: nil,
            delegateQueue: nil
        )

        guard let requestDefinition = self.requestDefinitions.randomElement() else {
            return
        }

        var request = URLRequest(url: requestDefinition.url,
                                 cachePolicy: .reloadIgnoringCacheData,
                                 timeoutInterval: 15.0)
        request.httpMethod = requestDefinition.method

        let task = session.dataTask(with: request)
        task.resume()
    }

    func simulateSpan() {
        let span = Logger.startSpan(
            name: "simulated_span",
            level: .debug,
            fields: [
                "common_field_key": "common_field_value",
                "test_key": "this should be overridden",
            ]
        )

        DispatchQueue.main.asyncAfter(deadline: .now().advanced(by: .milliseconds(500))) {
            span?.end(.success, fields: ["test_key": "test_value"])
        }
    }

    func simulateNavigation() {
        Logger.logScreenView(screenName: "First Screen")

        DispatchQueue.main.asyncAfter(deadline: .now().advanced(by: .milliseconds(500))) {
            Logger.logScreenView(screenName: "Second Screen")
        }
    }

    func log(with level: LogLevel) {
        let fields: [String: Encodable] = [
            "date_time": Date(),
            "json_field": EncodableExampleStruct(),
            "test_k": "test_v",
        ]

        switch level {
        case .error:
            Logger.logError("Sending log with level [Error]", fields: fields)
        case .warning:
            Logger.logWarning("Sending log with level [Warning]", fields: fields)
        case .info:
            Logger.logInfo("Sending log with level [Info]", fields: fields)
        case .debug:
            Logger.logDebug("Sending log with level [Debug]", fields: fields)
        case .trace:
            Logger.logTrace("Sending log with level [Trace]", fields: fields)
        }
    }

    func logAppLaunchTTI() {
        Logger.logAppLaunchTTI(Date().timeIntervalSince(self.appStartTime))
    }
}

extension LoggerCustomer: MXMetricManagerSubscriber {
    func didReceive(_ payloads: [MXMetricPayload]) {
        Capture.Logger.logDebug("Did Receive MXMetricPayloads")
        for payload in payloads {
            Capture.Logger.logDebug(
                "Did Receive MXMetricPayload",
                fields: ["payload": String(data: payload.jsonRepresentation(), encoding: .utf8)]
            )
        }
    }

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        Capture.Logger.logDebug("Did Receive MXDiagnosticPayloads")
        for payload in payloads {
            Capture.Logger.logDebug(
                "Did Receive MXDiagnosticPayload",
                fields: ["payload": String(data: payload.jsonRepresentation(), encoding: .utf8)]
            )
        }
    }
}

final class CustomFieldProvider: FieldProvider {
    let userID = UUID().uuidString

    func getFields() -> Fields {
        let invalidUTF8String = ("abc💇‍♀️" as NSString).substring(with: NSRange(location: 0, length: 4))
        return [
            "app": "hello_world",
            "user_id": self.userID,
            "invalid_utf8": invalidUTF8String,
        ]
    }
}

// Provides additional fields for each network request
struct CustomNetworkFieldProvider: URLSessionRequestFieldProvider {
    func provideExtraFields(for request: URLRequest) -> [String: String] {
        return [
            "additional_network_request_field": request.debugDescription,
        ]
    }
}
