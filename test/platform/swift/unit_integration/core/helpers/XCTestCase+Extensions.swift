// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CapturePassable
import Difference
import Foundation
import XCTest

extension XCTestCase {
    /// Creates a unique temporary directory for logger tests.
    ///
    /// This helper prevents "Failed to acquire directory lock" errors when multiple tests
    /// create loggers in quick succession by giving each logger instance its own isolated directory.
    ///
    /// - returns: A unique temporary directory URL
    func makeTemporaryLoggerDirectory() -> URL {
        return FileManager.default.temporaryDirectory
            .appendingPathComponent("bitdrift_test_\(UUID().uuidString)")
    }

    /// Starts a logger with a unique temporary directory to avoid directory lock conflicts.
    ///
    /// This is a convenience wrapper around `Logger.start()` that automatically provides
    /// a unique directory for each test invocation.
    ///
    /// - parameter apiKey:          The API key for the logger
    /// - parameter sessionStrategy: The session strategy to use
    /// - parameter configuration:   Optional configuration (rootFileURL will be overridden)
    /// - parameter fieldProviders:  Optional field providers
    /// - parameter dateProvider:    Optional date provider
    ///
    /// - returns: A LoggerIntegrator instance, or nil if logger creation failed
    @discardableResult
    func startLoggerWithIsolatedDirectory(
        apiKey: String = "test_api_key",
        sessionStrategy: SessionStrategy = .fixed(),
        configuration: Configuration = .init(),
        fieldProviders: [FieldProvider] = [],
        dateProvider: DateProvider? = nil
    ) -> LoggerIntegrator? {
        var config = configuration
        config.rootFileURL = makeTemporaryLoggerDirectory()

        return Logger.start(
            withAPIKey: apiKey,
            sessionStrategy: sessionStrategy,
            configuration: config,
            fieldProviders: fieldProviders,
            dateProvider: dateProvider
        )
    }

    func assertEqual(
        _ fields1: [String: String],
        _ fields2: Fields?,
        ignoredKeys: Set<String> = Set(),
        inFile file: String = #filePath,
        atLine line: Int = #line
    )
    {
        self.assertEqual(
            // swiftlint:disable force_try
            try! fields1.compactMap(Field.make),
            fields2.flatMap { try! $0.compactMap(Field.make) },
            ignoredKeys: ignoredKeys,
            inFile: file,
            atLine: line
        )
    }

    @objc(assertEqualWithFields1:fields2:ignoredKeys:inFile:atLine:)
    func assertEqual(
        _ fields1: [String: String],
        _ fields2: InternalFields,
        ignoredKeys: Set<String> = Set(),
        inFile file: String = #filePath,
        atLine line: Int = #line
    ) {
        self.assertEqual(
            // swiftlint:disable force_try
            try! fields1.compactMap(Field.make),
            fields2,
            ignoredKeys: ignoredKeys,
            inFile: file,
            atLine: line
        )
    }

    // swiftlint:disable function_body_length
    func assertEqual(
        _ fields1: InternalFields?,
        _ fields2: InternalFields?,
        ignoredKeys: Set<String> = Set(),
        inFile file: String = #filePath,
        atLine line: Int = #line
    )
    {
        guard let fields1, let fields2 else {
            if fields1 != fields2 {
                let description =
                    "\(String(describing: fields1)) is not equal to \(String(describing: fields2))"
                let issue = XCTIssue(
                    type: .assertionFailure,
                    compactDescription: description,
                    sourceCodeContext: XCTSourceCodeContext(
                        location: XCTSourceCodeLocation(filePath: file, lineNumber: line)
                    ),
                    associatedError: nil
                )
                self.record(issue)
            }

            return
        }

        let filtered1 = fields1
            .filter { !ignoredKeys.contains($0.key) }
            .sorted(by: { $0.key < $1.key })
        let filtered2 = fields2
            .filter { !ignoredKeys.contains($0.key) }
            .sorted(by: { $0.key < $1.key })

        let fail = {
            let issue = XCTIssue(
                type: .assertionFailure,
                compactDescription: "\(filtered2) is not equal to \(filtered1)",
                sourceCodeContext: XCTSourceCodeContext(
                    location: XCTSourceCodeLocation(filePath: file, lineNumber: line)
                ),
                associatedError: nil
            )
            self.record(issue)
        }

        if filtered1.count != filtered2.count {
            fail()
            return
        }

        let failKey = { (key: String) in
            let issue = XCTIssue(
                type: .assertionFailure,
                compactDescription: "Key: \(key), \(filtered2) is not equal to \(filtered1)",
                sourceCodeContext: XCTSourceCodeContext(
                    location: XCTSourceCodeLocation(filePath: file, lineNumber: line)
                ),
                associatedError: nil
            )
            self.record(issue)
        }

        for (index, field1) in filtered1.enumerated() {
            let field2 = filtered2[index]
            let value1 = field1.data
            let value2 = field2.data

            if let value1 = value1 as? String, let value2 = value2 as? String, value1 == value2 {
                continue
            } else if let value1 = value1 as? Data, let value2 = value2 as? Data, value1 == value2 {
                continue
            } else {
                failKey(field1.key)
                return
            }
        }
    }
}

public func XCTAssertEqual<T: Equatable>(
    _ expected: @autoclosure () throws -> T,
    _ received: @autoclosure () throws -> T,
    file: StaticString = #filePath,
    line: UInt = #line
) {
    do {
        let expected = try expected()
        let received = try received()
        XCTAssertTrue(
            expected == received,
            "Found difference for \n" + diff(expected, received).joined(separator: ", "),
            file: file,
            line: line
        )
    } catch {
        XCTFail("Caught error while testing: \(error)", file: file, line: line)
    }
}
