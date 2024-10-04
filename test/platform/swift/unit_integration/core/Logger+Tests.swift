// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import Foundation
import XCTest

extension Logger {
    static func testLogger(
        withAPIKey apiKey: String = "",
        bufferDirectory: URL?,
        sessionStrategy: SessionStrategy = .fixed(),
        dateProvider: DateProvider? = nil,
        fieldProviders: [FieldProvider] = [],
        configuration: Configuration,
        loggerBridgingFactoryProvider: LoggerBridgingFactoryProvider = LoggerBridgingFactory()
    ) throws -> Logger
    {
        return try XCTUnwrap(
            Logger(
                withAPIKey: apiKey,
                bufferDirectory: bufferDirectory,
                apiURL: URL(staticString: "https://api-tests.bitdrift.io"),
                remoteErrorReporter: nil,
                configuration: configuration,
                sessionStrategy: sessionStrategy,
                dateProvider: dateProvider,
                fieldProviders: fieldProviders,
                storageProvider: MockStorageProvider(),
                timeProvider: SystemTimeProvider(),
                loggerBridgingFactoryProvider: loggerBridgingFactoryProvider
            )
        )
    }

    /// Creates a new directory with a random UUID within the temp directory.
    ///
    /// - returns: The URL of the created directory.
    static func tempBufferDirectory() -> URL? {
        let fileManager = FileManager.default
        let directory = fileManager
            .temporaryDirectory
            .appendingPathComponent("capture", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)

        // Create buffer directory for persisting ring buffers
        let exists = FileManager.default.fileExists(atPath: directory.path)
        do {
            if !exists {
                // Create the Capture SDK directory, Rust logger will create the buffer directory within.
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            }
        } catch {
            assertionFailure("File write operation for buffer failed")
        }

        return directory
    }
}
