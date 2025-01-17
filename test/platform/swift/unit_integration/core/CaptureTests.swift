//
//  CaptureTests.swift
//  @//test/platform/swift/unit_integration/core:test
//
//  Created by Snow Pettersen on 1/17/25.
//

@testable import Capture
import CaptureTestBridge
import Foundation
import XCTest

final class CaptureTests: XCTestCase {
    func testLogCrashCalledBeforeStart() async throws {
        let testApiServerUrl = setUpTestServer()
        
        Capture.Logger.logCrash()
        Capture.Logger.start(withAPIKey: "api-key", sessionStrategy: .fixed(), apiURL: testApiServerUrl)
        
        let streamID = try await nextApiStream()
        configure_aggressive_continuous_uploads(streamID)
        
        let log = UploadedLog.captureNextLog()
        
        XCTAssertEqual(log?.message, "App Error Reported")
    }
    
    // swiftlint:disable:next test_case_accessibility
    func setUpTestServer(pingIntervalMs: Int32 = -1) -> URL {
        // The logger receives the ping interval to use in its handshake when it connects to the server,
        // so we pass the ping interval to the test server.
        let port = start_test_api_server(true, pingIntervalMs)
        // swiftlint:disable:next force_unwrapping
        return URL(string: "https://localhost:\(port)")!
    }

}
