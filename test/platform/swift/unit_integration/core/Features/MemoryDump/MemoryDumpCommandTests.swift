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
        thenFailsWithTitle("Memory dump is disabled")
    }

    func testInvalidArgumentsFail() async {
        givenMemoryDumpCommand()
        await whenExecuting(["stages": .string("stacks")])
        thenFailsWithCode("invalid_arguments")
    }

    func testAttachmentIsAMemoryDumpProto() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["stages": .string("counters")])
        let attachment = try thenAttachment()
        XCTAssertEqual(attachment.mimeType, "application/vnd.bitdrift.ios-memory-dump")
        XCTAssertFalse(attachment.data.isEmpty)
    }

    func testContextReportsStagesTimingAndFootprint() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["stages": .string("counters")])
        let context = try thenContext()
        XCTAssertEqual(context["stages"], "counters")
        XCTAssertNotNil(context["counters_ms"])
        XCTAssertGreaterThan(UInt64(try XCTUnwrap(context["phys_footprint_before"])) ?? 0, 0)
        XCTAssertGreaterThan(UInt64(try XCTUnwrap(context["phys_footprint_after"])) ?? 0, 0)
    }

    func testRegionsStageIsReportedInContextAndAttachment() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["stages": .string("regions")])
        let context = try thenContext()
        XCTAssertEqual(context["stages"], "regions")
        XCTAssertNotNil(context["regions_ms"])
        XCTAssertFalse(try thenAttachment().data.isEmpty)
    }

    func testHeapStageIsReportedInContextAndAttachment() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["stages": .string("heap"), "heap.budget_ms": .uint64(1_000)])
        let context = try thenContext()
        XCTAssertEqual(context["stages"], "heap")
        XCTAssertNotNil(context["heap_ms"])
        XCTAssertEqual(context["heap_truncated"], "false")
        XCTAssertNotNil(context["heap_zones_not_seen"])
        XCTAssertFalse(try thenAttachment().data.isEmpty)
    }

    func testSwiftGenericInstantiationsAreNamedMangled() async throws {
        genericArrays = (0..<500).map { [MemoryDumpElement(first: $0, second: $0)] }
        givenMemoryDumpCommand()
        await whenExecuting(["stages": .string("heap"), "heap.budget_ms": .uint64(1_000)])
        let payload = try thenAttachmentText()
        XCTAssertNotNil(payload.range(of: "_TtGC[A-Za-z0-9_]*MemoryDumpElement", options: .regularExpression))
    }

    func testPayloadCarriesNoPaths() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["heap.budget_ms": .uint64(1_000)])
        XCTAssertFalse(try thenAttachmentText().contains("/Users/"))
    }

    func testDisabledHeapIsSkippedAndReported() async throws {
        givenMemoryDumpCommand(heapEnabled: false)
        await whenExecuting(["stages": .string("counters,heap")])
        let context = try thenContext()
        XCTAssertEqual(context["heap"], "disabled")
        XCTAssertEqual(context["stages"], "counters")
        XCTAssertNil(context["heap_ms"])
    }
}

private extension MemoryDumpCommandTests {
    func givenMemoryDumpCommand(enabled: Bool = true, heapEnabled: Bool = true) {
        logger.mockRuntimeVariable(.memoryDumpEnabled, with: enabled)
        logger.mockRuntimeVariable(.memoryDumpHeapEnabled, with: heapEnabled)
        sut = MemoryDumpCommand(logger: logger)
    }

    func whenExecuting(_ arguments: CommandArguments = [:]) async {
        result = await sut.execute(arguments)
    }

    func thenFailsWithTitle(_ title: String) {
        guard case let .failure(error) = result else {
            return XCTFail("expected a failure, got \(String(describing: result))")
        }
        XCTAssertEqual(error.title, title)
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
