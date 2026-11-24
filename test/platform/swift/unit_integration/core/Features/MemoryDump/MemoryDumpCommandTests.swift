// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) @testable import Capture
@testable import CaptureMocks
import XCTest

struct MemoryDumpElement {
    var first = 0
    var second = 0
}

final class MemoryDumpCommandTests: XCTestCase {
    private let logger = MockCoreLogging()
    private var genericArrays: [[MemoryDumpElement]] = []
    private var sut: MemoryDumpCommand!
    private var result: Result<CommandResult, CommandError>!

    func testDisabledCommandFailsWithClearError() async {
        givenMemoryDumpCommand(enabled: false)
        await whenExecuting()
        thenFailsWithMessagePrefix("Memory dump is disabled: Enable ")
    }

    func testInvalidArgumentsFail() async {
        givenMemoryDumpCommand()
        await whenExecuting(["include_heap": .string("yes")])
        thenFailsWithCode("invalid_arguments")
    }

    func testAttachmentIsAMemoryDumpProto() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["include_heap": .bool(false)])
        let attachment = try thenAttachment()
        XCTAssertEqual(attachment.mimeType, "application/vnd.bitdrift.ios-memory-dump")
        XCTAssertFalse(attachment.data.isEmpty)
    }

    func testContextReportsStagesTimingAndFootprint() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["include_heap": .bool(false)])
        let context = try thenContext()
        XCTAssertEqual(context["stages"], "counters,regions")
        XCTAssertNotNil(context["counters_ms"])
        XCTAssertGreaterThan(UInt64(try XCTUnwrap(context["phys_footprint_before"])) ?? 0, 0)
        XCTAssertGreaterThan(UInt64(try XCTUnwrap(context["phys_footprint_after"])) ?? 0, 0)
    }

    func testExcludingTheHeapRunsOnlyCountersAndRegions() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["include_heap": .bool(false)])
        let context = try thenContext()
        XCTAssertEqual(context["stages"], "counters,regions")
        XCTAssertNotNil(context["regions_ms"])
        XCTAssertNil(context["heap_ms"])
        XCTAssertNil(context["heap"])
        XCTAssertFalse(try thenAttachment().data.isEmpty)
    }

    func testHeapStageIsReportedInContextAndAttachment() async throws {
        givenMemoryDumpCommand()
        await whenExecuting()
        let context = try thenContext()
        XCTAssertEqual(context["stages"], "counters,regions,heap")
        XCTAssertNotNil(context["heap_ms"])
        XCTAssertEqual(context["heap_truncated"], "false")
        XCTAssertNotNil(context["heap_zones_not_seen"])
        XCTAssertFalse(try thenAttachment().data.isEmpty)
    }

    func testSwiftGenericInstantiationsAreNamedMangled() async throws {
        genericArrays = (0..<500).map { [MemoryDumpElement(first: $0, second: $0)] }
        givenMemoryDumpCommand()
        await whenExecuting()
        let payload = try thenAttachmentText()
        XCTAssertNotNil(payload.range(of: "_TtGC[A-Za-z0-9_]*MemoryDumpElement", options: .regularExpression))
    }

    func testPayloadCarriesNoPaths() async throws {
        givenMemoryDumpCommand()
        await whenExecuting()
        XCTAssertFalse(try thenAttachmentText().contains("/Users/"))
    }

    func testDisabledHeapIsSkippedAndReported() async throws {
        givenMemoryDumpCommand(heapEnabled: false)
        await whenExecuting(["include_heap": .bool(true)])
        let context = try thenContext()
        XCTAssertEqual(context["heap"], "disabled")
        XCTAssertEqual(context["stages"], "counters,regions")
        XCTAssertNil(context["heap_ms"])
    }
}

private extension MemoryDumpCommandTests {
    func givenMemoryDumpCommand(enabled: Bool = true, heapEnabled: Bool = true) {
        logger.mockRuntimeVariable(.memoryDumpEnabled, with: enabled)
        logger.mockRuntimeVariable(.memoryDumpHeapEnabled, with: heapEnabled)
        sut = MemoryDumpCommand(logger: logger, heapBudgetMs: 1_000)
    }

    func whenExecuting(_ arguments: CommandArguments = [:]) async {
        result = await sut.execute(arguments)
    }

    func thenFailsWithMessagePrefix(_ prefix: String) {
        guard case let .failure(error) = result else {
            return XCTFail("expected a failure, got \(String(describing: result))")
        }
        XCTAssertTrue(error.message.hasPrefix(prefix), error.message)
    }

    func thenFailsWithCode(_ code: String) {
        guard case let .failure(error) = result else {
            return XCTFail("expected a failure, got \(String(describing: result))")
        }
        XCTAssertEqual(error.code, code)
    }

    func thenContext() throws -> [String: String] {
        try result.get().context
    }

    func thenAttachment() throws -> CommandAttachment {
        try XCTUnwrap(try result.get().attachment)
    }

    func thenAttachmentText() throws -> String {
        String(decoding: try thenAttachment().data, as: UTF8.self)
    }
}
