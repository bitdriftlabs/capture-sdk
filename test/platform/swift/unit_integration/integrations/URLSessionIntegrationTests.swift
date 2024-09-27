// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

// swiftlint:disable file_length

private final class URLSessionDelegate: NSObject, URLSessionTaskDelegate {
    var didCreateTaskExpectation: XCTestExpectation?
    var didFinishCollectingMetricsExpectation: XCTestExpectation?
    var didCompleteExpectation: XCTestExpectation?

    func urlSession(_: URLSession, didCreateTask _: URLSessionTask) {
        self.didCreateTaskExpectation?.fulfill()
    }

    func urlSession(
        _: URLSession,
        task _: URLSessionTask,
        didFinishCollecting _: URLSessionTaskMetrics
    ) {
        self.didFinishCollectingMetricsExpectation?.fulfill()
    }

    func urlSession(_: URLSession, task _: URLSessionTask, didCompleteWithError _: Error?) {
        self.didCompleteExpectation?.fulfill()
    }
}

struct URLSessionTaskTestCaseInput {
    let task: URLSessionTask
    let completionExpectation: XCTestExpectation
}

// swiftlint:disable:next type_body_length
final class URLSessionIntegrationTests: XCTestCase {
    private var logger: MockLogging!

    override func setUp() {
        super.setUp()
        self.customSetUp()
    }

    override func tearDown() {
        super.tearDown()
        self.customTearDown()
    }

    private func customSetUp() {
        self.logger = MockLogging()
        Logger.resetShared(logger: self.logger)
        Logger
            .configure(withAPIKey: "123", sessionStrategy: .fixed())?
            .enableIntegrations([
                .urlSession(),
            ])
    }

    private func customTearDown() {
        Logger.resetShared()
    }

    func testCreateDataTask() throws {
        let requestExpectation = self.expectation(description: "request not logged")
        requestExpectation.isInverted = true
        let responseExpectation = self.expectation(description: "response not logged")
        responseExpectation.isInverted = true

        URLSession.shared.dataTask(with: self.makeURLRequest()) { _, _, _ in }

        XCTAssertEqual(
            .completed,
            XCTWaiter().wait(for: [requestExpectation, responseExpectation], timeout: 2)
        )

        XCTAssertTrue(self.logger.logs.isEmpty)
    }

    // MARK: - Tasks

    func testCustomSessionTasks() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp()

            let expectation = self.expectation(description: "delegate callbacks are called")
            expectation.expectedFulfillmentCount = 2

            let taskCompletionExpectation = self.expectation(description: "task completed")

            let delegate = URLSessionDelegate()
            delegate.didCreateTaskExpectation = expectation
            delegate.didFinishCollectingMetricsExpectation = expectation
            delegate.didCompleteExpectation = taskCompletionExpectation

            let session = URLSession(configuration: .default, delegate: delegate, delegateQueue: nil)

            let task = try taskTestCase(session)

            try self.runCompletedRequestTest(with: task, completionExpectation: taskCompletionExpectation)

            XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 0.1))

            self.customTearDown()
            session.finishTasksAndInvalidate()
        }
    }

    func testCustomCaptureSessionTasks() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp()

            let expectation = self.expectation(description: "delegate callbacks are called")
            expectation.expectedFulfillmentCount = 2

            let taskCompletionExpectation = self.expectation(description: "task completed")

            let delegate = URLSessionDelegate()
            delegate.didCreateTaskExpectation = expectation
            delegate.didFinishCollectingMetricsExpectation = expectation
            delegate.didCompleteExpectation = taskCompletionExpectation

            let session = URLSession(
                instrumentedSessionWithConfiguration: .default,
                delegate: delegate
            )
            let task = try taskTestCase(session)

            try self.runCompletedRequestTest(with: task, completionExpectation: taskCompletionExpectation)

            XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 0.1))

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    func testSharedSessionTasks() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp()

            let task = try taskTestCase(.shared)

            try self.runCompletedRequestTest(with: task, completionExpectation: nil)

            self.customTearDown()
        }
    }

    // MARK: - Tasks With Task Delegates

    @available(iOS 15.0, *)
    func testCustomSessionTasksWithTaskDelegates() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp()

            let expectation = self.expectation(description: "delegate callback is called")
            let taskCompletionExpectation = self.expectation(description: "task completed")

            let delegate = URLSessionDelegate()
            delegate.didFinishCollectingMetricsExpectation = expectation
            delegate.didCompleteExpectation = taskCompletionExpectation

            let session = URLSession(configuration: .default)

            let task = try taskTestCase(session)
            task.delegate = delegate

            try self.runCompletedRequestTest(with: task, completionExpectation: taskCompletionExpectation)

            XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 0.1))

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    @available(iOS 15.0, *)
    func testSharedSessionTasksWithTaskDelegates() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp()

            let expectation = self.expectation(description: "delegate callback is called")
            let taskCompletionExpectation = self.expectation(description: "task completed")

            let delegate = URLSessionDelegate()
            delegate.didFinishCollectingMetricsExpectation = expectation
            delegate.didCompleteExpectation = taskCompletionExpectation

            let task = try taskTestCase(.shared)
            task.delegate = delegate

            try self.runCompletedRequestTest(with: task, completionExpectation: taskCompletionExpectation)

            XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 0.1))

            self.customTearDown()
        }
    }

    // MARK: Tasks With Completion Closures

    func testSharedSessionTasksWithCompletionClosures() throws {
        for taskTestCase in self.makeTaskWithCompletionClosureTestCases() {
            self.customSetUp()

            let testCaseInput = try taskTestCase(.shared)

            try self.runCompletedRequestTest(
                with: testCaseInput.task,
                completionExpectation: testCaseInput.completionExpectation
            )

            self.customTearDown()
        }
    }

    func testCustomSessionTasksWithCompletionClosures() throws {
        for taskTestCase in self.makeTaskWithCompletionClosureTestCases() {
            self.customSetUp()

            let expectation = self.expectation(description: "delegate callbacks are called")
            expectation.expectedFulfillmentCount = 2

            let delegate = URLSessionDelegate()
            delegate.didCreateTaskExpectation = expectation
            delegate.didFinishCollectingMetricsExpectation = expectation

            let session = URLSession(configuration: .default, delegate: delegate, delegateQueue: nil)

            let testCaseInput = try taskTestCase(session)

            try self.runCompletedRequestTest(
                with: testCaseInput.task,
                completionExpectation: testCaseInput.completionExpectation
            )

            XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 0.1))

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    // MARK: - Cancelling Tasks

    func testCancelRequestsSharedSession() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp()

            let task = try taskTestCase(.shared)
            try self.runCanceledRequestTest(with: task, taskCompletionExpectation: nil)
        }
    }

    func testCancelRequestsCustomSession() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp()

            let session = URLSession(configuration: .default)

            let task = try taskTestCase(session)
            try self.runCanceledRequestTest(with: task, taskCompletionExpectation: nil)

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    // MARK: - Cancelling Tasks With Task Delegates

    @available(iOS 15.0, *)
    func testCancelRequestsSharedSessionWithTaskDelegates() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp()

            let expectation = self.expectation(description: "task completes")
            let delegate = URLSessionDelegate()
            delegate.didCompleteExpectation = expectation

            let task = try taskTestCase(.shared)
            task.delegate = delegate

            try self.runCanceledRequestTest(with: task, taskCompletionExpectation: expectation)

            self.customTearDown()
        }
    }

    @available(iOS 15.0, *)
    func testCancelRequestsCustomSessionWithTaskDelegates() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp()

            let session = URLSession(configuration: .default)

            let expectation = self.expectation(description: "task completes")
            let delegate = URLSessionDelegate()
            delegate.didCompleteExpectation = expectation

            let task = try taskTestCase(session)
            task.delegate = delegate

            try self.runCanceledRequestTest(with: task, taskCompletionExpectation: expectation)

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    // MARK: - Cancelling Tasks With Completion Closures

    func testCancelRequestsSharedSessionWithCompletionClosures() throws {
        for taskTestCase in self.makeTaskWithCompletionClosureTestCases() {
            self.customSetUp()

            let testCaseInput = try taskTestCase(.shared)

            try self.runCanceledRequestTest(
                with: testCaseInput.task,
                taskCompletionExpectation: testCaseInput.completionExpectation
            )

            self.customTearDown()
        }
    }

    func testCancelRequestsCustomSessionWithCompletionClosures() throws {
        for taskTestCase in self.makeTaskWithCompletionClosureTestCases() {
            self.customSetUp()

            let session = URLSession(configuration: .default)
            let testCaseInput = try taskTestCase(session)

            try self.runCanceledRequestTest(
                with: testCaseInput.task,
                taskCompletionExpectation: testCaseInput.completionExpectation
            )

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    // MARK: - Cancelling Tasks With Task Delegates And Completion Closures

    @available(iOS 15.0, *)
    func testCancelRequestsSharedSessionWithTaskDelegatesAndCompletionClosures() throws {
        for taskTestCase in self.makeTaskWithCompletionClosureTestCases() {
            self.customSetUp()

            let expectation = self.expectation(description: "task did collect metrics")
            let delegate = URLSessionDelegate()
            delegate.didFinishCollectingMetricsExpectation = expectation

            let testCaseInput = try taskTestCase(.shared)
            testCaseInput.task.delegate = delegate

            try self.runCanceledRequestTest(
                with: testCaseInput.task,
                taskCompletionExpectation: testCaseInput.completionExpectation
            )

            XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 1))

            self.customTearDown()
        }
    }

    @available(iOS 15.0, *)
    func testCancelRequestsCustomSessionWithTaskDelegatesAndCompletionClosures() throws {
        for taskTestCase in self.makeTaskWithCompletionClosureTestCases() {
            self.customSetUp()

            let session = URLSession(configuration: .default)

            let expectation = self.expectation(description: "task did collect metrics")
            let delegate = URLSessionDelegate()
            delegate.didFinishCollectingMetricsExpectation = expectation

            let testCaseInput = try taskTestCase(session)
            testCaseInput.task.delegate = delegate

            try self.runCanceledRequestTest(
                with: testCaseInput.task,
                taskCompletionExpectation: testCaseInput.completionExpectation
            )

            XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 1))

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    // MARK: - Tasks Without Logging Support (Stream and WebSocket Tasks)

    func testStreamedTasksWithoutLoggingSupportSharedSession() {
        for taskTestCase in self.makeTaskWithoutLoggingSupportTestCases() {
            let task = taskTestCase(.shared)

            task.resume()

            let logRequestExpectation = self.expectation(description: "request is not logged")
            logRequestExpectation.isInverted = true
            self.logger.logRequestExpectation = logRequestExpectation

            let logResponseExpectation = self.expectation(description: "response is not logged")
            logResponseExpectation.isInverted = true
            self.logger.logResponseExpectation = logResponseExpectation

            XCTAssertEqual(
                .completed,
                XCTWaiter().wait(for: [logRequestExpectation, logResponseExpectation], timeout: 2.0)
            )

            XCTAssertTrue(self.logger.logs.isEmpty)

            self.customTearDown()
        }
    }

    func testTasksWithoutLoggingSupportCustomSession() throws {
        for taskTestCase in self.makeTaskWithoutLoggingSupportTestCases() {
            let session = URLSession(configuration: .default)
            let task = taskTestCase(session)

            task.resume()

            let logRequestExpectation = self.expectation(description: "request is not logged")
            logRequestExpectation.isInverted = true
            self.logger.logRequestExpectation = logRequestExpectation

            let logResponseExpectation = self.expectation(description: "response is not logged")
            logResponseExpectation.isInverted = true
            self.logger.logResponseExpectation = logResponseExpectation

            XCTAssertEqual(
                .completed,
                XCTWaiter().wait(for: [logRequestExpectation, logResponseExpectation], timeout: 2.0)
            )

            XCTAssertTrue(self.logger.logs.isEmpty)

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    // MARK: -

    func testDataTaskPublisher() throws {
        let publisher = URLSession.shared.dataTaskPublisher(for: self.makeURL())

        let logRequestExpectation = self.expectation(description: "request is logged")
        let logResponseExpectation = self.expectation(description: "response is logged")

        self.logger.logRequestExpectation = logRequestExpectation
        self.logger.logResponseExpectation = logResponseExpectation

        let expectation = self.expectation(description: "task completes")

        let task = publisher.sink { _ in } receiveValue: { _, _ in
            expectation.fulfill()
        }

        let expectations = [logRequestExpectation, logResponseExpectation, expectation]

        XCTAssertEqual(.completed, XCTWaiter().wait(for: expectations, timeout: 5.0))

        XCTAssertEqual(2, self.logger.logs.count)

        let response = self.logger.logs[1].response()
        XCTAssertNotNil(response)

        _ = self.removeNonComparableExpectedResponseFields(
            try XCTUnwrap((response?.toFields() ?? [:]) as? [String: String])
        )

        task.cancel()
    }

    func testDoesNotEmitLogsForCaptureSDKNetworkRequests() {
        let session = URLSession(configuration: .default)
        var urlRequest = self.makeURLRequest()
        urlRequest.addValue("true", forHTTPHeaderField: "x-capture-api")

        let taskCompletionExpectation = self.expectation(description: "request completes")
        let task = session.dataTask(with: urlRequest) { _, _, _ in taskCompletionExpectation.fulfill() }

        let logRequestExpectation = self.expectation(description: "request is not logged")
        logRequestExpectation.isInverted = true
        let logResponseExpectation = self.expectation(description: "response is not logged")
        logResponseExpectation.isInverted = true

        self.logger.logRequestExpectation = logRequestExpectation
        self.logger.logResponseExpectation = logResponseExpectation

        task.resume()

        XCTAssertEqual(
            .completed,
            XCTWaiter().wait(
                for: [logRequestExpectation, logResponseExpectation, taskCompletionExpectation],
                timeout: 10
            )
        )

        session.invalidateAndCancel()
    }

    // MARK: - Parametrized Test Methods

    private func runCompletedRequestTest(
        with task: URLSessionTask,
        completionExpectation: XCTestExpectation?
    ) throws
    {
        let logRequestExpectation = self.expectation(description: "request logged")
        let logResponseExpectation = self.expectation(description: "response logged")

        self.logger.logRequestExpectation = logRequestExpectation
        self.logger.logResponseExpectation = logResponseExpectation

        task.resume()

        var expectations = [
            logRequestExpectation,
            logResponseExpectation,
        ]

        if let completionExpectation {
            expectations.append(completionExpectation)
        }

        XCTAssertEqual(.completed, XCTWaiter().wait(for: expectations, timeout: 10, enforceOrder: false))

        XCTAssertEqual(2, self.logger.logs.count)

        let requestInfo = try XCTUnwrap(self.logger.logs[0].request())
        var requestInfoFields = try XCTUnwrap(requestInfo.toFields() as? [String: String])

        // Confirm that `_span_id` key exists but remove it before we perform fields comparison
        let requestInfoSpanID = try XCTUnwrap(requestInfoFields["_span_id"])
        requestInfoFields["_span_id"] = nil

        XCTAssertEqual(
            [
                "_host": "www.google.com",
                "_method": "GET",
                "_path": "/search",
                "_query": "q=test",
            ],
            requestInfoFields
        )

        // Confirm that `_span_id` key exists but remove it before we perform fields comparison
        let responseInfo = try XCTUnwrap(self.logger.logs[1].response())
        var responseInfoFields = try XCTUnwrap(responseInfo.toFields() as? [String: String])

        // Confirm that request and response span IDs are equal
        let responseInfoSpanID = try XCTUnwrap(responseInfoFields.removeValue(forKey: "_span_id"))
        XCTAssertEqual(requestInfoSpanID, responseInfoSpanID)

        // Confirm that fields exists without confirming their exact values.
        let remainingResponseFields = self.removeNonComparableExpectedResponseFields(responseInfoFields)

        XCTAssertEqual(
            [
                "_host": "www.google.com",
                "_method": "GET",
                "_path": "/search",
                "_query": "q=test",
                "_result": "success",
                "_status_code": "200",
            ],
            remainingResponseFields
        )
    }

    private func runCanceledRequestTest(
        with task: URLSessionTask,
        taskCompletionExpectation: XCTestExpectation?
    ) throws
    {
        let logRequestExpectation = self.expectation(description: "request logged")
        let logResponseExpectation = self.expectation(description: "response logged")

        self.logger.logRequestExpectation = logRequestExpectation
        self.logger.logResponseExpectation = logResponseExpectation

        task.resume()
        task.cancel()

        var expectations = [
            logRequestExpectation,
            logResponseExpectation,
        ]

        if let taskCompletionExpectation {
            expectations.append(taskCompletionExpectation)
        }

        XCTAssertEqual(.completed, XCTWaiter().wait(for: expectations, timeout: 10))

        XCTAssertEqual(2, self.logger.logs.count)
        let requestInfo = try XCTUnwrap(self.logger.logs[0].request())
        var requestInfoFields = try XCTUnwrap(requestInfo.toFields() as? [String: String])

        // Confirm that `_span_id` key exists but remove it before we perform fields comparison
        let requestInfoSpanID = try XCTUnwrap(requestInfoFields["_span_id"])
        requestInfoFields["_span_id"] = nil

        XCTAssertEqual(
            [
                "_host": "www.google.com",
                "_method": "GET",
                "_path": "/search",
                "_query": "q=test",
            ],
            requestInfoFields
        )

        // Confirm that `_span_id` key exists but remove it before we perform fields comparison
        let responseInfo = try XCTUnwrap(logger.logs[1].response())
        var responseInfoFields = try XCTUnwrap(responseInfo.toFields() as? [String: String])

        // Confirm that request and response span IDs are equal
        let responseInfoSpanID = try XCTUnwrap(responseInfoFields.removeValue(forKey: "_span_id"))
        XCTAssertEqual(requestInfoSpanID, responseInfoSpanID)

        // Confirm that fields exists without confirming their exact values.
        let remainingResponseFields = self.removeNonComparableExpectedResponseFields(responseInfoFields)

        XCTAssertEqual(
            [
                "_host": "www.google.com",
                "_method": "GET",
                "_path": "/search",
                "_query": "q=test",
                "_result": "canceled",
            ],
            remainingResponseFields
        )
    }

    // MARK: - Task Test Cases Creation Methods

    private func makeTaskWithoutCompletionClosureTestCases() -> [(URLSession) throws -> URLSessionTask] {
        return [
            { session in session.dataTask(with: self.makeURLRequest()) },
            { session in session.dataTask(with: self.makeURL()) },
            { session in session.uploadTask(with: self.makeURLRequest(), from: Data()) },
            { session in
                let url = try self.makeTempFileURL(name: "test_file")
                return session.uploadTask(with: self.makeURLRequest(), fromFile: url)
            },
            { session in session.downloadTask(with: self.makeURLRequest()) },
            { session in session.downloadTask(with: self.makeURL()) },
        ]
    }

    private func makeTaskWithCompletionClosureTestCases()
    -> [(URLSession) throws -> URLSessionTaskTestCaseInput]
    {
        return [
            self.makeURLDataTaskTestCase(session:),
            self.makeURLRequestDataTaskTestCase(session:),
            self.makeURLRequestDataUploadTaskTestCase(session:),
            self.makeURLRequestFileUploadTaskTestCase(session:),
            self.makeURLRequestDownloadTaskTestCase(session:),
            self.makeURLDownloadTaskTestCase(session:),
        ]
    }

    private func makeTaskWithoutLoggingSupportTestCases() -> [(URLSession) -> URLSessionTask] {
        return [
            { session in
                let request = URLRequest(url: URL(staticString: "ws://bitdrift.io"))
                return session.webSocketTask(with: request)
            },
            { session in session.webSocketTask(with: URL(staticString: "ws://bitdrift.io")) },
            { session in session.streamTask(withHostName: "bitdrift.io", port: 123) },
        ]
    }

    private func makeURLDataTaskTestCase(session: URLSession) throws -> URLSessionTaskTestCaseInput {
        let taskCompletionExpectation = self.expectation(description: "url task completed")
        let task = session.dataTask(with: self.makeURL()) { _, _, _ in
            taskCompletionExpectation.fulfill()
        }

        return URLSessionTaskTestCaseInput(task: task, completionExpectation: taskCompletionExpectation)
    }

    private func makeURLRequestDataTaskTestCase(session: URLSession) throws -> URLSessionTaskTestCaseInput {
        let taskCompletionExpectation = self.expectation(description: "urlRequest task completed")
        let task = session.dataTask(with: self.makeURLRequest()) { _, _, _ in
            taskCompletionExpectation.fulfill()
        }

        return URLSessionTaskTestCaseInput(task: task, completionExpectation: taskCompletionExpectation)
    }

    private func makeURLRequestDataUploadTaskTestCase(session: URLSession) throws
    -> URLSessionTaskTestCaseInput
    {
        let taskCompletionExpectation = self.expectation(description: "urlRequest data upload task completed")
        let task = session.uploadTask(with: self.makeURLRequest(), from: Data()) { _, _, _ in
            taskCompletionExpectation.fulfill()
        }

        return URLSessionTaskTestCaseInput(task: task, completionExpectation: taskCompletionExpectation)
    }

    private func makeURLRequestFileUploadTaskTestCase(session: URLSession) throws
    -> URLSessionTaskTestCaseInput
    {
        let url = try self.makeTempFileURL(name: "test_file")

        let taskCompletionExpectation = self.expectation(description: "urlRequest file upload task completed")
        let task = session.uploadTask(with: self.makeURLRequest(), fromFile: url) { _, _, _ in
            taskCompletionExpectation.fulfill()
        }

        return URLSessionTaskTestCaseInput(task: task, completionExpectation: taskCompletionExpectation)
    }

    private func makeURLRequestDownloadTaskTestCase(session: URLSession) -> URLSessionTaskTestCaseInput {
        let taskCompletionExpectation = self.expectation(description: "urlRequest download task completed")
        let task = session.downloadTask(with: self.makeURLRequest()) { _, _, _ in
            taskCompletionExpectation.fulfill()
        }

        return URLSessionTaskTestCaseInput(task: task, completionExpectation: taskCompletionExpectation)
    }

    private func makeURLDownloadTaskTestCase(session: URLSession) -> URLSessionTaskTestCaseInput {
        let taskCompletionExpectation = self.expectation(description: "urlRequest download task completed")
        let task = session.downloadTask(with: self.makeURL()) { _, _, _ in
            taskCompletionExpectation.fulfill()
        }

        return URLSessionTaskTestCaseInput(task: task, completionExpectation: taskCompletionExpectation)
    }

    // MARK: - Private Helpers

    private func removeNonComparableExpectedResponseFields(_ fields: [String: String]) -> [String: String] {
        var fields = fields

        let expectedFields = [
            "_duration_ms",
            "_request_body_bytes_sent_count",
            "_response_body_bytes_received_count",
            "_request_headers_bytes_count",
            "_response_headers_bytes_count",
        ]

        for field in expectedFields {
            XCTAssertNotNil(fields.removeValue(forKey: field), "missing field: \(field)")
        }

        let expectedOptionalFields = [
            "_dns_resolution_duration_ms",
        ]

        for field in expectedOptionalFields {
            fields.removeValue(forKey: field)
        }

        return fields
    }

    private func makeURLRequest() -> URLRequest {
        return URLRequest(url: self.makeURL())
    }

    private func makeURL() -> URL {
        // `https` requires us to run tests inside of a host application which causes the tests to take
        // significantly more time when ran on the CI.
        // TODO(Augustyniak): Move to using bitdrift ping.
        // swiftlint:disable:next force_unwrapping
        return URL(string: "http://www.google.com/search?q=test")!
    }

    private func makeTempFileURL(name: String) throws -> URL {
        let url = try self.makeTempDirectoryURL()

        let fileURL: URL
        if #available(iOS 16.0, *) {
            fileURL = url.appending(path: name)
        } else {
            fileURL = url.appendingPathComponent(name)
        }

        XCTAssertTrue(FileManager.default.createFile(atPath: fileURL.path, contents: nil))
        return fileURL
    }

    private func makeTempDirectoryURL() throws -> URL {
        let documentDirectories = NSSearchPathForDirectoriesInDomains(
            .cachesDirectory,
            .userDomainMask,
            true
        )

        let documentDirectory = try XCTUnwrap(documentDirectories.first)

        let url = URL(fileURLWithPath: documentDirectory, isDirectory: true)
            .appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
}
