// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import AVFoundation
@testable import Capture
import CaptureMocks
import Foundation
import XCTest

// MARK: - Test Helpers

extension XCTestCase {
    /// Creates a unique temporary directory for logger tests to avoid directory lock conflicts.
    ///
    /// - returns: A unique temporary directory URL
    fileprivate func makeTemporaryLoggerDirectory() -> URL {
        return FileManager.default.temporaryDirectory
            .appendingPathComponent("bitdrift_test_\(UUID().uuidString)")
    }
}

// swiftlint:disable file_length
private final class URLSessionIncompleteDelegate: NSObject, URLSessionTaskDelegate {
    var didCompleteExpectation: XCTestExpectation?

    func urlSession(_: URLSession, task _: URLSessionTask, didCompleteWithError _: Error?) {
        self.didCompleteExpectation?.fulfill()
    }
}

private final class URLSessionCustomDelegate: NSObject, URLSessionDelegate {
    var didReceiveChallenge: XCTestExpectation?

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge
    ) async -> (URLSession.AuthChallengeDisposition, URLCredential?) {
        self.didReceiveChallenge?.fulfill()
        return (.performDefaultHandling, nil)
    }
}

private final class URLSessionCustomTaskDelegate: NSObject, URLSessionTaskDelegate {
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
        self.customSetUp(swizzle: true)
    }

    override func tearDown() {
        super.tearDown()
        self.customTearDown()
    }

    private func customSetUp(swizzle: Bool) {
        self.logger = MockLogging()
        URLSessionIntegration.shared.disableURLSessionTaskSwizzling()

        Logger.resetShared(logger: self.logger)

        Logger
            .start(
                withAPIKey: "123",
                sessionStrategy: .fixed(),
                configuration: .init(rootFileURL: self.makeTemporaryLoggerDirectory())
            )?
            .enableIntegrations([.urlSession()], disableSwizzling: !swizzle)
    }

    private func customTearDown() {
        URLSessionIntegration.shared.disableURLSessionTaskSwizzling()
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

    func testResumeDataTaskUnswizzled() throws {
        self.customSetUp(swizzle: false)

        let requestExpectation = self.expectation(description: "request not logged")
        requestExpectation.isInverted = true
        let responseExpectation = self.expectation(description: "response not logged")
        responseExpectation.isInverted = true

        let task = URLSession.shared.dataTask(with: self.makeURLRequest()) { _, _, _ in }
        task.resume()

        XCTAssertEqual(
            .completed,
            XCTWaiter().wait(for: [requestExpectation, responseExpectation], timeout: 2)
        )

        XCTAssertTrue(self.logger.logs.isEmpty)
    }

    // MARK: - Tasks

    func testCustomSessionTasks() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: true)

            let expectation = self.expectation(description: "delegate callbacks are called")
            expectation.expectedFulfillmentCount = 2

            let taskCompletionExpectation = self.expectation(description: "task completed")

            let delegate = URLSessionCustomTaskDelegate()
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

    func testCreateInvalidTask() throws {
        self.customSetUp(swizzle: true)

        let requestExpectation = self.expectation(description: "request not logged")
        requestExpectation.isInverted = true
        let responseExpectation = self.expectation(description: "response not logged")
        responseExpectation.isInverted = true

        let session = AVAssetDownloadURLSession(configuration: .background(withIdentifier: "w00t"),
                                                assetDownloadDelegate: nil,
                                                delegateQueue: nil)

        let task = session
            .makeAssetDownloadTask(downloadConfiguration: .init(asset: .init(url: self.makeURL()),
                                                                title: "we"))
        task.resume()

        XCTAssertEqual(
            .completed,
            XCTWaiter().wait(for: [requestExpectation, responseExpectation], timeout: 2)
        )

        XCTAssertTrue(self.logger.logs.isEmpty)
    }

    func testBackgroundSessionTasks() throws {
        self.customSetUp(swizzle: true)

        let session = URLSession(configuration: .background(withIdentifier: "w00t"))
        let task = session.dataTask(with: self.makeURL())

        let logRequestExpectation = self.expectation(
            description: "request logged"
        )
        self.logger.logRequestExpectation = logRequestExpectation
        task.resume()

        XCTAssertEqual(
            .completed,
            XCTWaiter().wait(for: [logRequestExpectation], timeout: 3, enforceOrder: false)
        )
        XCTAssertEqual(1, self.logger.logs.count)

        let requestInfo = try XCTUnwrap(self.logger.logs[0].request())
        var requestInfoFields = try XCTUnwrap(requestInfo.toFields() as? [String: String])
        requestInfoFields["_span_id"] = nil

        XCTAssertEqual(
            [
                "_host": "api-fe.bitdrift.io",
                "_method": "GET",
                "_path": "/fe/ping",
                "_query": "q=test",
                "_span_type": "start",
            ],
            requestInfoFields
        )

        self.customTearDown()
        session.finishTasksAndInvalidate()
    }

    func testCustomCaptureSessionTasks() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: false)

            let expectation = self.expectation(description: "delegate callbacks are called")
            expectation.expectedFulfillmentCount = 2

            let taskCompletionExpectation = self.expectation(description: "task completed")

            let delegate = URLSessionCustomTaskDelegate()
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

    func testCustomCaptureSessionTasksWithNonTaskDelegate() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: false)

            let expectation = self.expectation(description: "delegate callbacks are called")
            expectation.expectedFulfillmentCount = 1

            let delegate = URLSessionCustomDelegate()
            delegate.didReceiveChallenge = expectation

            let session = URLSession(
                instrumentedSessionWithConfiguration: .default,
                delegate: delegate
            )
            let task = try taskTestCase(session)

            try self.runCompletedRequestTest(with: task, completionExpectation: nil)

            XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 0.1))

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    func testCustomDelegateWithoutAllMethods() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: false)

            let taskCompletionExpectation = self.expectation(description: "task completed")

            let delegate = URLSessionIncompleteDelegate()
            delegate.didCompleteExpectation = taskCompletionExpectation

            let session = URLSession(
                instrumentedSessionWithConfiguration: .default,
                delegate: delegate
            )
            let task = try taskTestCase(session)

            try self.runCompletedRequestTest(with: task, completionExpectation: taskCompletionExpectation)

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    func testSharedSessionTasks() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: true)

            let task = try taskTestCase(.shared)

            try self.runCompletedRequestTest(with: task, completionExpectation: nil)

            self.customTearDown()
        }
    }

    // MARK: - Tasks With Task Delegates

    func testCustomSessionTasksWithTaskDelegates() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: true)

            let expectation = self.expectation(description: "delegate callback is called")
            let taskCompletionExpectation = self.expectation(description: "task completed")

            let delegate = URLSessionCustomTaskDelegate()
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

    func testSharedSessionTasksWithTaskDelegates() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: true)

            let expectation = self.expectation(description: "delegate callback is called")
            let taskCompletionExpectation = self.expectation(description: "task completed")

            let delegate = URLSessionCustomTaskDelegate()
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
            self.customSetUp(swizzle: true)

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
            self.customSetUp(swizzle: true)

            let expectation = self.expectation(description: "delegate callbacks are called")
            expectation.expectedFulfillmentCount = 2

            let delegate = URLSessionCustomTaskDelegate()
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
            self.customSetUp(swizzle: true)

            let task = try taskTestCase(.shared)
            try self.runCanceledRequestTest(with: task, taskCompletionExpectation: nil)
        }
    }

    func testCancelRequestsCustomSession() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: true)

            let session = URLSession(configuration: .default)

            let task = try taskTestCase(session)
            try self.runCanceledRequestTest(with: task, taskCompletionExpectation: nil)

            session.invalidateAndCancel()
            self.customTearDown()
        }
    }

    // MARK: - Cancelling Tasks With Task Delegates

    func testCancelRequestsSharedSessionWithTaskDelegates() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: true)

            let expectation = self.expectation(description: "task completes")
            let delegate = URLSessionCustomTaskDelegate()
            delegate.didCompleteExpectation = expectation

            let task = try taskTestCase(.shared)
            task.delegate = delegate

            try self.runCanceledRequestTest(with: task, taskCompletionExpectation: expectation)

            self.customTearDown()
        }
    }

    func testCancelRequestsCustomSessionWithTaskDelegates() throws {
        for taskTestCase in self.makeTaskWithoutCompletionClosureTestCases() {
            self.customSetUp(swizzle: true)

            let session = URLSession(configuration: .default)

            let expectation = self.expectation(description: "task completes")
            let delegate = URLSessionCustomTaskDelegate()
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
            self.customSetUp(swizzle: true)

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
            self.customSetUp(swizzle: true)

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

    func testCancelRequestsSharedSessionWithTaskDelegatesAndCompletionClosures() throws {
        for taskTestCase in self.makeTaskWithCompletionClosureTestCases() {
            self.customSetUp(swizzle: true)

            let expectation = self.expectation(description: "task did collect metrics")
            let delegate = URLSessionCustomTaskDelegate()
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

    func testCancelRequestsCustomSessionWithTaskDelegatesAndCompletionClosures() throws {
        for taskTestCase in self.makeTaskWithCompletionClosureTestCases() {
            self.customSetUp(swizzle: true)

            let session = URLSession(configuration: .default)

            let expectation = self.expectation(description: "task did collect metrics")
            let delegate = URLSessionCustomTaskDelegate()
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

        XCTAssertEqual(.completed, XCTWaiter().wait(for: expectations, timeout: 3, enforceOrder: false))

        XCTAssertEqual(2, self.logger.logs.count)

        let requestInfo = try XCTUnwrap(self.logger.logs[0].request())
        var requestInfoFields = try XCTUnwrap(requestInfo.toFields() as? [String: String])

        // Confirm that `_span_id` key exists but remove it before we perform fields comparison
        let requestInfoSpanID = try XCTUnwrap(requestInfoFields["_span_id"])
        requestInfoFields["_span_id"] = nil

        XCTAssertEqual(
            [
                "_host": "api-fe.bitdrift.io",
                "_method": "GET",
                "_path": "/fe/ping",
                "_query": "q=test",
                "_span_type": "start",
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
                "_host": "api-fe.bitdrift.io",
                "_method": "GET",
                "_path": "/fe/ping",
                "_query": "q=test",
                "_result": "success",
                "_span_type": "end",
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
                "_host": "api-fe.bitdrift.io",
                "_method": "GET",
                "_path": "/fe/ping",
                "_query": "q=test",
                "_span_type": "start",
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
                "_host": "api-fe.bitdrift.io",
                "_method": "GET",
                "_path": "/fe/ping",
                "_query": "q=test",
                "_result": "canceled",
                "_span_type": "end",
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
            "_tls_duration_ms",
            "_tcp_duration_ms",
            "_fetch_init_duration_ms",
            "_response_latency_ms",
            "_protocol",
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
        return URL(string: "https://api-fe.bitdrift.io/fe/ping?q=test")!
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

final class URLSessionTracePropagationTests: XCTestCase {
    private var loggerBridge: MockLoggerBridging!

    override func setUp() {
        super.setUp()
        self.loggerBridge = MockLoggerBridging()
        URLSessionIntegration.shared.disableURLSessionTaskSwizzling()

        let logger: Logger? = Logger(
            withAPIKey: "123",
            remoteErrorReporter: nil,
            configuration: .init(rootFileURL: FileManager.default.temporaryDirectory.appendingPathComponent("bitdrift_test_\(UUID().uuidString)")),
            sessionStrategy: .fixed(),
            dateProvider: nil,
            fieldProviders: [],
            enableNetwork: false,
            storageProvider: MockStorageProvider(),
            timeProvider: MockTimeProvider(),
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: self.loggerBridge)
        )
        guard let sharedLogger = logger else {
            XCTFail("failed to initialize test logger")
            return
        }
        Logger.resetShared(logger: sharedLogger)

        URLSessionIntegration.shared.start(
            logger: sharedLogger,
            disableSwizzling: false,
            requestFieldProvider: nil,
            responseFieldProvider: nil
        )
    }

    override func tearDown() {
        URLSessionIntegration.shared.disableURLSessionTaskSwizzling()
        Logger.resetShared()
        super.tearDown()
    }

    func testCapResume_whenTracingInactive_shouldNotAttachTraceContext() throws {
        self.loggerBridge.tracingActive = false
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "w3c")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))

        task.resume()

        XCTAssertNil(task.cap_traceContext)
        task.cancel()
        session.invalidateAndCancel()
    }

    func testCapResume_whenModeDisabled_shouldNotAttachTraceContext() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "none")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))

        task.resume()

        XCTAssertNil(task.cap_traceContext)
        task.cancel()
        session.invalidateAndCancel()
    }

    func testCapResume_whenW3CEnabled_shouldAttachTraceContext() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "w3c")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        XCTAssertEqual(32, traceContext.traceID.count)
        XCTAssertEqual(16, traceContext.spanID.count)
        task.cancel()
        session.invalidateAndCancel()
    }

    func testCapResume_whenW3CEnabled_shouldInjectTraceparentHeader() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "w3c")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        let headers = task.originalRequest?.allHTTPHeaderFields
        let traceparent = try XCTUnwrap(headers?["traceparent"])
        XCTAssertEqual(traceparent, "00-\(traceContext.traceID)-\(traceContext.spanID)-01")

        let captureHeader = try XCTUnwrap(headers?["x-capture-span-trace-id"])
        XCTAssertEqual(captureHeader, traceContext.traceID)

        XCTAssertNil(headers?["b3"])
        XCTAssertNil(headers?["X-B3-TraceId"])

        task.cancel()
        session.invalidateAndCancel()
    }

    func testCapResume_whenB3SingleEnabled_shouldInjectB3Header() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "b3-single")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        XCTAssertEqual(32, traceContext.traceID.count)
        XCTAssertEqual(16, traceContext.spanID.count)

        let headers = task.originalRequest?.allHTTPHeaderFields
        let b3 = try XCTUnwrap(headers?["b3"])
        XCTAssertEqual(b3, "\(traceContext.traceID)-\(traceContext.spanID)-1")

        let captureHeader = try XCTUnwrap(headers?["x-capture-span-trace-id"])
        XCTAssertEqual(captureHeader, traceContext.traceID)

        XCTAssertNil(headers?["traceparent"])
        XCTAssertNil(headers?["X-B3-TraceId"])

        task.cancel()
        session.invalidateAndCancel()
    }

    func testCapResume_whenB3MultiEnabled_shouldInjectB3MultiHeaders() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "b3-multi")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        XCTAssertEqual(32, traceContext.traceID.count)
        XCTAssertEqual(16, traceContext.spanID.count)

        let headers = task.originalRequest?.allHTTPHeaderFields
        XCTAssertEqual(headers?["X-B3-TraceId"], traceContext.traceID)
        XCTAssertEqual(headers?["X-B3-SpanId"], traceContext.spanID)
        XCTAssertEqual(headers?["X-B3-Sampled"], "1")

        let captureHeader = try XCTUnwrap(headers?["x-capture-span-trace-id"])
        XCTAssertEqual(captureHeader, traceContext.traceID)

        XCTAssertNil(headers?["traceparent"])
        XCTAssertNil(headers?["b3"])

        task.cancel()
        session.invalidateAndCancel()
    }

    // MARK: - Existing header detection

    func testCapResume_withExistingTraceparent_shouldExtractTraceIDAndSkipInjection() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "w3c")

        var request = URLRequest(url: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))
        request.setValue("00-abcdef1234567890abcdef1234567890-1234567890abcdef-01", forHTTPHeaderField: "traceparent")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: request)

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        XCTAssertEqual(traceContext.traceID, "abcdef1234567890abcdef1234567890")
        XCTAssertEqual(traceContext.spanID, "")

        let headers = task.originalRequest?.allHTTPHeaderFields
        XCTAssertEqual(headers?["traceparent"], "00-abcdef1234567890abcdef1234567890-1234567890abcdef-01")
        XCTAssertNil(headers?["x-capture-span-trace-id"])

        task.cancel()
        session.invalidateAndCancel()
    }

    func testCapResume_withExistingB3Single_shouldExtractTraceIDAndSkipInjection() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "b3-single")

        var request = URLRequest(url: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))
        request.setValue("abcdef1234567890abcdef1234567890-1234567890abcdef-1", forHTTPHeaderField: "b3")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: request)

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        XCTAssertEqual(traceContext.traceID, "abcdef1234567890abcdef1234567890")
        XCTAssertEqual(traceContext.spanID, "")

        let headers = task.originalRequest?.allHTTPHeaderFields
        XCTAssertEqual(headers?["b3"], "abcdef1234567890abcdef1234567890-1234567890abcdef-1")
        XCTAssertNil(headers?["x-capture-span-trace-id"])

        task.cancel()
        session.invalidateAndCancel()
    }

    func testCapResume_withExistingB3Multi_shouldExtractTraceIDAndSkipInjection() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "b3-multi")

        var request = URLRequest(url: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))
        request.setValue("abcdef1234567890abcdef1234567890", forHTTPHeaderField: "X-B3-TraceId")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: request)

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        XCTAssertEqual(traceContext.traceID, "abcdef1234567890abcdef1234567890")
        XCTAssertEqual(traceContext.spanID, "")

        let headers = task.originalRequest?.allHTTPHeaderFields
        XCTAssertEqual(headers?["X-B3-TraceId"], "abcdef1234567890abcdef1234567890")
        XCTAssertNil(headers?["X-B3-SpanId"])
        XCTAssertNil(headers?["X-B3-Sampled"])
        XCTAssertNil(headers?["x-capture-span-trace-id"])

        task.cancel()
        session.invalidateAndCancel()
    }

    // MARK: - Cross-format existing header detection

    func testCapResume_withW3CMode_andExistingB3Header_shouldExtractFromB3() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "w3c")

        var request = URLRequest(url: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))
        request.setValue("abcdef1234567890abcdef1234567890-1234567890abcdef-1", forHTTPHeaderField: "b3")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: request)

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        XCTAssertEqual(traceContext.traceID, "abcdef1234567890abcdef1234567890")
        XCTAssertEqual(traceContext.spanID, "")

        let headers = task.originalRequest?.allHTTPHeaderFields
        XCTAssertNil(headers?["traceparent"])

        task.cancel()
        session.invalidateAndCancel()
    }

    func testCapResume_withB3Mode_andExistingTraceparent_shouldExtractFromW3C() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "b3-single")

        var request = URLRequest(url: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))
        request.setValue("00-abcdef1234567890abcdef1234567890-1234567890abcdef-01", forHTTPHeaderField: "traceparent")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: request)

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        XCTAssertEqual(traceContext.traceID, "abcdef1234567890abcdef1234567890")
        XCTAssertEqual(traceContext.spanID, "")

        let headers = task.originalRequest?.allHTTPHeaderFields
        XCTAssertNil(headers?["b3"])

        task.cancel()
        session.invalidateAndCancel()
    }

    func testCapResume_withB3MultiMode_andExistingB3Single_shouldExtractFromB3() throws {
        self.loggerBridge.tracingActive = true
        self.loggerBridge.mockRuntimeVariable(.tracePropagationMode, with: "b3-multi")

        var request = URLRequest(url: URL(staticString: "https://api-fe.bitdrift.io/fe/ping?q=test"))
        request.setValue("abcdef1234567890abcdef1234567890-1234567890abcdef-1", forHTTPHeaderField: "b3")

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: request)

        task.resume()

        let traceContext = try XCTUnwrap(task.cap_traceContext)
        XCTAssertEqual(traceContext.traceID, "abcdef1234567890abcdef1234567890")
        XCTAssertEqual(traceContext.spanID, "")

        let headers = task.originalRequest?.allHTTPHeaderFields
        XCTAssertNil(headers?["X-B3-TraceId"])

        task.cancel()
        session.invalidateAndCancel()
    }
}

// MARK: - extractExistingTraceID unit tests

final class URLSessionTracePropagationExtractionTests: XCTestCase {
    func testExtractExistingTraceID_fromW3CTraceparent() {
        let headers = ["traceparent": "00-abcdef1234567890abcdef1234567890-1234567890abcdef-01"]
        let traceID = URLSessionTracePropagation.extractExistingTraceID(from: headers)
        XCTAssertEqual(traceID, "abcdef1234567890abcdef1234567890")
    }

    func testExtractExistingTraceID_fromB3Single() {
        let headers = ["b3": "abcdef1234567890abcdef1234567890-1234567890abcdef-1"]
        let traceID = URLSessionTracePropagation.extractExistingTraceID(from: headers)
        XCTAssertEqual(traceID, "abcdef1234567890abcdef1234567890")
    }

    func testExtractExistingTraceID_fromB3Multi() {
        let headers = ["X-B3-TraceId": "abcdef1234567890abcdef1234567890"]
        let traceID = URLSessionTracePropagation.extractExistingTraceID(from: headers)
        XCTAssertEqual(traceID, "abcdef1234567890abcdef1234567890")
    }

    func testExtractExistingTraceID_prefersW3COverB3() {
        let headers = [
            "traceparent": "00-w3ctraceida1b2c3d4e5f6a7b8c9d0-1234567890abcdef-01",
            "b3": "b3traceida1b2c3d4e5f6a7b8c9d0e1f2-1234567890abcdef-1",
        ]
        let traceID = URLSessionTracePropagation.extractExistingTraceID(from: headers)
        XCTAssertEqual(traceID, "w3ctraceida1b2c3d4e5f6a7b8c9d0")
    }

    func testExtractExistingTraceID_returnsNilForNoHeaders() {
        XCTAssertNil(URLSessionTracePropagation.extractExistingTraceID(from: nil))
    }

    func testExtractExistingTraceID_returnsNilForEmptyHeaders() {
        XCTAssertNil(URLSessionTracePropagation.extractExistingTraceID(from: [:]))
    }

    func testExtractExistingTraceID_returnsNilForUnrelatedHeaders() {
        let headers = ["Content-Type": "application/json", "Authorization": "Bearer token"]
        XCTAssertNil(URLSessionTracePropagation.extractExistingTraceID(from: headers))
    }
}
