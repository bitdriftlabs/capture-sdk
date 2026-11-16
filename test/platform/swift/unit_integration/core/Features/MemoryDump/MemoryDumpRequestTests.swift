// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) @testable import Capture
import XCTest

final class MemoryDumpRequestTests: XCTestCase {
    private var arguments: CommandArguments = [:]
    private var request: MemoryDumpRequest?
    private var error: CommandError?

    func testNoArgumentsIncludeTheHeap() {
        givenArguments([:])
        whenParsing()
        thenRequestEquals(MemoryDumpRequest(includeHeap: true))
        thenStagesAre([.counters, .regions, .heap])
    }

    func testExcludingTheHeapKeepsCountersAndRegions() {
        givenArguments(["include_heap": .bool(false)])
        whenParsing()
        thenRequestEquals(MemoryDumpRequest(includeHeap: false))
        thenStagesAre([.counters, .regions])
    }

    func testRemovedOptionsAreRejected() {
        for name in ["stages", "heap.budget_ms", "heap.owners", "regions.mode", "heap.limit"] {
            givenArguments([name: .uint64(1)])
            whenParsing()
            thenFailsWithInvalidArguments(mentioning: name)
            request = nil
            error = nil
        }
    }

    func testWrongArgumentTypeIsRejected() {
        givenArguments(["include_heap": .string("true")])
        whenParsing()
        thenFailsWithInvalidArguments(mentioning: "include_heap")
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

    func thenFailsWithInvalidArguments(mentioning text: String) {
        XCTAssertNil(request)
        XCTAssertEqual(error?.code, "invalid_arguments")
        XCTAssertTrue(
            error?.description?.contains(text) == true,
            "'\(error?.description ?? "")' does not mention '\(text)'"
        )
    }
}
