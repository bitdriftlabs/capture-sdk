// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI)
@testable import Capture
import CaptureLoggerBridge
import Foundation
import XCTest

final class StackProfileCommandTests: XCTestCase {
    private let arguments: CommandArguments = [
        "frequency_hz": .uint64(20), "duration_ms": .uint64(500), "buffer_size_kb": .uint64(256),
    ]

    func testValidatesParametersBeforeCreatingTheBridge() async {
        let command = StackProfileCommand(makeBridge: {
            XCTFail("invalid parameters must not construct the bridge")
            return StackCaptureBridge()
        }).make()
        var cases: [CommandArguments] = [[:]]
        for key in ["frequency_hz", "duration_ms", "buffer_size_kb"] {
            let invalidValues: [CommandArgument] = [
                .uint64(0), .int64(-1), .double(1), .string("1"), .uint64(UInt64(UInt32.max) + 1),
            ]
            for invalid in invalidValues {
                var value = arguments
                value[key] = invalid
                cases.append(value)
            }
            var missing = arguments
            missing.removeValue(forKey: key)
            cases.append(missing)
        }
        var excessiveFrequency = arguments
        excessiveFrequency["frequency_hz"] = .uint64(1_000_000_001)
        cases.append(excessiveFrequency)
        var extra = arguments
        extra["extra"] = .bool(true)
        cases.append(extra)
        for value in cases {
            guard case let .failure(error) = await command.handler(value) else {
                return XCTFail("expected invalid arguments")
            }
            XCTAssertEqual(error.code, CommandError.invalidArguments().code)
        }
    }

    func testPassesFrequencyDurationAndPositiveBudgetWithoutClamping() async {
        let bridge = ProfileBridgeStub { frequency, duration, budget, _ in
            XCTAssertEqual(frequency, 20)
            XCTAssertEqual(duration, 500)
            return .failure(CommandError(message: String(budget)))
        }
        let command = StackProfileCommand(makeBridge: { bridge }).make()
        for budget: UInt64 in [1, 15, 16385, UInt64(UInt32.max)] {
            var value = arguments
            value["buffer_size_kb"] = .uint64(budget)
            guard case let .failure(error) = await command.handler(value) else {
                return XCTFail("expected fixture result")
            }
            XCTAssertEqual(error.message, String(budget))
        }
    }

    func testProfileAndSnapshotShareTheSlotUntilCancelledWorkerFinishes() async {
        let started = expectation(description: "profile started")
        let cancelled = expectation(description: "profile cancelled")
        let finish = DispatchSemaphore(value: 0)
        defer { finish.signal() }
        let bridge = ProfileBridgeStub(profile: { _, _, _, _ in
            started.fulfill()
            finish.wait()
            return .failure(CommandError(message: "fixture finished"))
        }, cancel: { cancelled.fulfill() })
        let profile = StackProfileCommand(makeBridge: { bridge }).make()
        let blockedProfile = StackProfileCommand(makeBridge: {
            XCTFail("busy profile must reject before bridge creation")
            return bridge
        }).make()
        let blockedSnapshot = StackSnapshotCommand(makeBridge: {
            XCTFail("busy snapshot must reject before bridge creation")
            return StackCaptureBridge()
        }).make()
        let task = Task { await profile.handler(arguments) }
        await fulfillment(of: [started], timeout: 5)
        for cancel in [false, true] {
            if cancel {
                task.cancel()
                await fulfillment(of: [cancelled], timeout: 5)
            }
            let profileResult = await blockedProfile.handler(arguments)
            let snapshotResult = await blockedSnapshot.handler(["buffer_size_kb": .uint64(256)])
            for result in [profileResult, snapshotResult] {
                guard case let .failure(error) = result else {
                    finish.signal()
                    _ = await task.value
                    return XCTFail("expected busy rejection")
                }
                XCTAssertEqual(error.code, CommandError.alreadyExecuting.code)
            }
        }
        finish.signal()
        guard case let .failure(error) = await task.value else {
            return XCTFail("expected cancellation")
        }
        XCTAssertEqual(error.message, "cancelled")
        // Use a fresh nonblocking bridge to verify the shared slot was released.
        let released = StackSnapshotCommand(makeBridge: { ProfileSnapshotFailure() }).make()
        guard case let .failure(nextError) = await released.handler(["buffer_size_kb": .uint64(256)]) else {
            return XCTFail("expected fixture result")
        }
        XCTAssertEqual(nextError.message, "released")
    }

    func testCancelledRequestDoesNotConstructTheBridge() async {
        let command = StackProfileCommand(makeBridge: {
            XCTFail("cancelled request must not construct the bridge")
            return StackCaptureBridge()
        }).make()
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return await command.handler(arguments)
        }
        guard case let .failure(error) = await task.value else {
            return XCTFail("expected cancellation")
        }
        XCTAssertEqual(error.message, "cancelled")
    }

    func testPeriodicAttachmentContainsMultipleSamplesAndSummary() async throws {
        #if !arch(arm64)
        let result = await StackProfileCommand().make().handler(arguments)
        guard case let .failure(error) = result else {
            return XCTFail("expected unsupported architecture")
        }
        XCTAssertEqual(error.message, "unsupported")
        #else
        let result = try await StackProfileCommand().make().handler(arguments).get()
        let attachment = try XCTUnwrap(result.attachment)
        defer { attachment.discardOwnedFile() }
        let url = try XCTUnwrap(attachment.fileURL)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let meta = try XCTUnwrap(json["bitdrift"] as? [String: Any])
        XCTAssertEqual(meta["capture"] as? String, "profile")
        let rounds = try XCTUnwrap(meta["captureRounds"] as? Int)
        XCTAssertGreaterThan(rounds, 1)
        XCTAssertEqual(result.context["capture_rounds"], String(rounds))
        let threads = try XCTUnwrap(json["threads"] as? [[String: Any]])
        XCTAssertTrue(threads.contains { thread in
            guard let samples = thread["samples"] as? [String: Any],
                  let data = samples["data"] as? [[Any]] else { return false }
            return data.count > 1
        })
        for thread in threads {
            let samples = try XCTUnwrap(thread["samples"] as? [String: Any])
            let data = try XCTUnwrap(samples["data"] as? [[NSNumber]])
            let times = data.map { $0[1].doubleValue }
            XCTAssertEqual(times, times.sorted())
        }
        #endif
    }
}

private final class ProfileBridgeStub: StackProfileBridging, @unchecked Sendable {
    private let onProfile: @Sendable (
        UInt32, UInt32, UInt32, URL
    ) -> Result<StackProfileSummary, CommandError>
    private let onCancel: @Sendable () -> Void

    init(
        profile: @escaping @Sendable (
            UInt32, UInt32, UInt32, URL
        ) -> Result<StackProfileSummary, CommandError>,
        cancel: @escaping @Sendable () -> Void = {}
    ) {
        onProfile = profile
        onCancel = cancel
    }

    func profile(frequencyHz: UInt32, durationMs: UInt32, bufferSizeKb: UInt32, to url: URL)
    -> Result<StackProfileSummary, CommandError> {
        onProfile(frequencyHz, durationMs, bufferSizeKb, url)
    }

    func cancel() { onCancel() }
}

private final class ProfileSnapshotFailure: StackSnapshotBridging, @unchecked Sendable {
    func capture(bufferSizeKb: UInt32, to url: URL) -> Result<StackSnapshotSummary, CommandError> {
        .failure(CommandError(message: "released"))
    }
    func cancel() {}
}
