// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class MemoryDumpRequestTests: XCTestCase {
    private var arguments: CommandArguments = [:]
    private var request: MemoryDumpRequest?
    private var error: CommandError?

    func testNoArgumentsUsesDefaults() {
        givenArguments([:])
        whenParsing()
        thenRequestEquals(MemoryDumpRequest())
        thenStagesAre([.counters, .regions, .heap])
    }

    func testExplicitArgumentsOverrideDefaults() {
        givenArguments([
            "stages": .string("regions"),
            "heap.budget_ms": .uint64(100),
            "heap.owners": .bool(false),
        ])
        whenParsing()
        thenRequestEquals(MemoryDumpRequest(
            stages: [.regions],
            heap: .init(budgetMs: 100, owners: false)
        ))
    }

    func testRemovedOptionsAreRejected() {
        for name in ["regions.mode", "regions.limit", "heap.limit", "heap.webkit"] {
            givenArguments([name: .uint64(1)])
            whenParsing()
            thenFailsWithInvalidArguments(mentioning: name)
            request = nil
            error = nil
        }
    }

    func testStagesRunInExecutionOrderWithoutDuplicates() {
        givenArguments(["stages": .string("heap, counters,heap")])
        whenParsing()
        thenStagesAre([.counters, .heap])
    }

    func testUnknownArgumentIsRejected() {
        givenArguments(["regions.depth": .uint64(1)])
        whenParsing()
        thenFailsWithInvalidArguments(mentioning: "regions.depth")
    }

    func testWrongArgumentTypeIsRejected() {
        givenArguments(["heap.owners": .string("true")])
        whenParsing()
        thenFailsWithInvalidArguments(mentioning: "heap.owners")
    }

    func testUnknownStageIsRejected() {
        givenArguments(["stages": .string("counters,stacks")])
        whenParsing()
        thenFailsWithInvalidArguments(mentioning: "stacks")
    }

    func testEmptyStageIsRejected() {
        givenArguments(["stages": .string("counters,")])
        whenParsing()
        thenFailsWithInvalidArguments(mentioning: "stage")
    }

    func testHeapBudgetIsClampedBelowTheCommandTimeout() {
        givenArguments(["heap.budget_ms": .uint64(60_000)])
        whenParsing()
        thenHeapBudgetIs(MemoryDumpRequest.maximumHeapBudgetMs)
    }

    func testZeroHeapBudgetIsAccepted() {
        givenArguments(["heap.budget_ms": .uint64(0)])
        whenParsing()
        thenHeapBudgetIs(0)
    }
}

private extension MemoryDumpRequestTests {
    func givenArguments(_ arguments: CommandArguments) {
        self.arguments = arguments
    }

    func whenParsing() {
        do {
            request = try MemoryDumpRequest(arguments: arguments)
        } catch {
            self.error = error
        }
    }

    func thenRequestEquals(_ expected: MemoryDumpRequest) {
        XCTAssertNil(error)
        XCTAssertEqual(request, expected)
    }

    func thenStagesAre(_ stages: [MemoryDumpRequest.Stage]) {
        XCTAssertNil(error)
        XCTAssertEqual(request?.stages, stages)
    }

    func thenHeapBudgetIs(_ budgetMs: UInt64) {
        XCTAssertNil(error)
        XCTAssertEqual(request?.heap.budgetMs, budgetMs)
    }

    func thenFailsWithInvalidArguments(mentioning text: String) {
        XCTAssertNil(request)
        XCTAssertEqual(error?.code, "invalid_arguments")
        XCTAssertTrue(
            error?.description?.contains(text) == true,
            "'\(error?.description ?? "")' does not mention '\(text)'"
        )
    }
}
