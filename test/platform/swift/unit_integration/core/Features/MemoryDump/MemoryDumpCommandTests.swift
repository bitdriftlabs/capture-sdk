// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
@testable import CaptureMocks
import XCTest

final class MemoryDumpCommandTests: XCTestCase {
    private let logger = MockCoreLogging()
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

    func testCountersReportFootprint() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["stages": .string("counters")])
        let counters = try thenAttachmentSection("counters")
        XCTAssertGreaterThan(try XCTUnwrap(counters["phys_footprint"] as? UInt64), 0)
        XCTAssertGreaterThan(try XCTUnwrap(counters["resident_size"] as? UInt64), 0)
    }

    func testAttachmentIsJSONWithSchemaVersion() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["stages": .string("counters")])
        let attachment = try thenAttachment()
        XCTAssertEqual(attachment.mimeType, "application/json")
        let json = try thenAttachmentJSON()
        XCTAssertEqual(json["schema_version"] as? Int, MemoryDumpReport.currentSchemaVersion)
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
        let regions = try thenAttachmentSection("regions")
        XCTAssertNil(regions["mode"])
        XCTAssertNotNil(regions["compressed"])
        XCTAssertFalse(try XCTUnwrap(regions["by_tag"] as? [Any]).isEmpty)
        XCTAssertFalse(try XCTUnwrap(regions["by_image"] as? [Any]).isEmpty)
        XCTAssertNil(try thenAttachmentJSON()["counters"])
    }

    func testHeapStageIsReportedInContextAndAttachment() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["stages": .string("heap"), "heap.budget_ms": .uint64(1_000)])
        let context = try thenContext()
        XCTAssertEqual(context["stages"], "heap")
        XCTAssertNotNil(context["heap_ms"])
        XCTAssertEqual(context["heap_truncated"], "false")
        XCTAssertNotNil(context["heap_zones_not_seen"])
        let heap = try thenAttachmentSection("heap")
        XCTAssertGreaterThan(try XCTUnwrap(heap["total_blocks"] as? UInt64), 0)
        XCTAssertFalse(try XCTUnwrap(heap["by_type"] as? [Any]).isEmpty)
        XCTAssertFalse(try XCTUnwrap(heap["by_image"] as? [Any]).isEmpty)
    }

    func testPayloadCarriesNoAddresses() async throws {
        givenMemoryDumpCommand()
        await whenExecuting(["heap.budget_ms": .uint64(1_000)])
        let payload = try XCTUnwrap(String(data: try thenAttachment().data, encoding: .utf8))
        XCTAssertFalse(payload.contains("0x"))
        XCTAssertFalse(payload.contains("unknown context at"))
    }

    func testDisabledHeapIsSkippedAndReported() async throws {
        givenMemoryDumpCommand(heapEnabled: false)
        await whenExecuting(["stages": .string("counters,heap")])
        let context = try thenContext()
        XCTAssertEqual(context["heap"], "disabled")
        XCTAssertEqual(context["stages"], "counters")
        XCTAssertNil(context["heap_ms"])
        XCTAssertNil(try thenAttachmentJSON()["heap"])
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

    func thenAttachmentJSON() throws -> [String: Any] {
        let object = try JSONSerialization.jsonObject(with: try thenAttachment().data)
        return try XCTUnwrap(object as? [String: Any])
    }

    func thenAttachmentSection(_ name: String) throws -> [String: Any] {
        try XCTUnwrap(try thenAttachmentJSON()[name] as? [String: Any])
    }
}
