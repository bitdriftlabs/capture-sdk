// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import Darwin
import Foundation

private let kDeviceId = "ios-helloworld"
private let kEntityID = "hello-world-entity-id"

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

        let issueCallbackConfiguration = IssueCallbackConfiguration(
            callbackQueue: .main,
            issueReportCallback: CustomerIssueReportCallback()
        )

        Logger
            .start(
                withAPIKey: Configuration.storedAPIKey ?? "",
                sessionStrategy: .activityBased(inactivityThresholdMins: 30, onSessionIDChanged: { sessionId in
                    print("Session changed: \(sessionId)")
                }),
                configuration: Capture.Configuration(
                    apiURL: apiURL,
                    issueCallbackConfiguration: issueCallbackConfiguration
                ),
                fieldProviders: [CustomFieldProvider()],
                startResult: { result in
                    switch result {
                    case .success(let logger):
                        print("SDK started successfully. " +
                                "sessionID=\(logger.sessionID), " +
                                "sessionURL=\(logger.sessionURL), " +
                                "deviceID=\(logger.deviceID)")
                    case .failure(let error):
                        print("SDK failed to start: \(error)")
                    }
                }
            )?
            .enableIntegrations(
                [.urlSession(
                    requestFieldProvider: CustomNetworkFieldProvider(),
                    responseFieldProvider: CustomNetworkResponseFieldProvider()
                ), ],
                disableSwizzling: false
            )

        Logger.addField(withKey: "field_container_field_key", value: "field_container_value")
        Logger.setEntityID(kEntityID)
        Logger.logInfo("App launched. Logger configured.")

        if let previousRunInfo = Capture.Logger.previousRunInfo {
            Capture.Logger.logInfo(
                "Bitdrift PreviousRunInfo",
                fields: [
                    "hasFatallyTerminated": String(previousRunInfo.hasFatallyTerminated),
                ]
            )
        }
    }

    func startNewSession() {
        Logger.startNewSession()
    }

    func createTemporaryDeviceCode(completion: @escaping (Result<String, Error>) -> Void) {
        Logger.createTemporaryDeviceCode(completion: completion)
    }

    func setFeatureFlagExposure(name: String, variant: String) {
        Capture.Logger.setFeatureFlagExposure(withName: name, variant: variant)
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

    func log(with level: LogLevel, message: String? = nil) {
        let fields: [String: Encodable] = [
            "date_time": Date(),
            "json_field": EncodableExampleStruct(),
            "test_k": "test_v",
        ]

        let trimmedMessage = message?.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedMessage = trimmedMessage?.isEmpty == false
            ? trimmedMessage!
            : "Sending log with level [\(level.rawValue.capitalized)]"

        switch level {
        case .error:
            Logger.logError(resolvedMessage, fields: fields)
        case .warning:
            Logger.logWarning(resolvedMessage, fields: fields)
        case .info:
            Logger.logInfo(resolvedMessage, fields: fields)
        case .debug:
            Logger.logDebug(resolvedMessage, fields: fields)
        case .trace:
            Logger.logTrace(resolvedMessage, fields: fields)
        }
    }

    func logAppLaunchTTI() {
        Logger.logAppLaunchTTI(Date().timeIntervalSince(self.appStartTime))
    }

    private var memoryPressureAllocations: [Data] = []

    func forceMemoryPressure(targetPercent: Int) {
        memoryPressureAllocations.removeAll()

        let availableMemory = os_proc_available_memory()
        guard availableMemory > 0 else { return }

        var taskInfo = task_vm_info_data_t()
        var count = mach_msg_type_number_t(
            MemoryLayout<task_vm_info>.stride / MemoryLayout<integer_t>.stride
        )
        let result: kern_return_t = withUnsafeMutablePointer(to: &taskInfo) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: 1) { taskInfoOut in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), taskInfoOut, &count)
            }
        }
        guard result == KERN_SUCCESS else { return }

        let currentUsed = taskInfo.phys_footprint
        let appLimit = currentUsed + UInt64(availableMemory)
        let targetUsed = UInt64(Double(appLimit) * Double(targetPercent) / 100.0)

        guard targetUsed > currentUsed else {
            Logger.logInfo("Already at or above \(targetPercent)% memory usage")
            return
        }

        let bytesToAllocate = targetUsed - currentUsed
        let chunkSize = 10 * 1024 * 1024
        let chunks = Int(bytesToAllocate) / chunkSize

        Logger.logInfo("Allocating ~\(bytesToAllocate / 1024 / 1024) MB to reach \(targetPercent)%")

        for _ in 0..<chunks {
            let data = Data(repeating: 0xAB, count: chunkSize)
            memoryPressureAllocations.append(data)
        }

        Logger.logInfo("Memory pressure increased to ~\(targetPercent)%")
    }

    func clearMemoryPressure() {
        memoryPressureAllocations.removeAll()
        Logger.logInfo("Memory pressure cleared")
    }

    func simulateLowMemoryWarning(level: String) {
        var taskInfo = task_vm_info_data_t()
        var count = mach_msg_type_number_t(
            MemoryLayout<task_vm_info>.stride / MemoryLayout<integer_t>.stride
        )
        let result: kern_return_t = withUnsafeMutablePointer(to: &taskInfo) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: 1) { taskInfoOut in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), taskInfoOut, &count)
            }
        }
        let usedKB = result == KERN_SUCCESS ? taskInfo.phys_footprint / 1_024 : 0
        NotificationCenter.default.post(
            name: Notification.Name("io.bitdrift.capture.simulate_memory_pressure"),
            object: nil,
            userInfo: ["level": level, "memoryUsedKB": usedKB]
        )
        Logger.logInfo("Simulated memory pressure: level=\(level), usedKB=\(usedKB)")
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

// Provides additional fields for each network response
struct CustomNetworkResponseFieldProvider: URLSessionResponseFieldProvider {
    func provideExtraFields(for response: HTTPURLResponse) -> [String: String] {
        return [
            "additional_network_response_field": response.debugDescription,
        ]
    }
}

private final class CustomerIssueReportCallback: IssueReportCallback {
    func onBeforeReportSend(report: IssueReport) {
        Capture.Logger.logInfo(
            "Callback issue Report occurred \(report.details): \(report.reason)",
            fields: [
                "reportType": report.reportType,
                "session": report.sessionID,
                "details": report.details,
                "reason": report.reason,
                "fields": report.fields.description,
            ]
        )
    }
}
