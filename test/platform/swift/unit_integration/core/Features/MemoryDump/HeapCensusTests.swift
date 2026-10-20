// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMemoryCore
import Darwin
import XCTest

final class HeapCensusProbe {
    var value = 0
}

final class HeapCensusOwner {
    static let bufferSize = 4_096
    let buffer: UnsafeMutableRawPointer

    init() {
        buffer = calloc(1, Self.bufferSize)!
    }

    deinit {
        free(buffer)
    }
}

struct HeapCensusElement {
    var first = 0
    var second = 0
}

final class HeapCensusTests: XCTestCase {
    private var retained: [AnyObject] = []
    private var genericArrays: [[HeapCensusElement]] = []
    private var buffers: [UnsafeMutableRawPointer] = []
    private var sections: [HeapCensus.Section] = []
    private var summary: bdmem_census_summary_t?
    private var mapper: RegionMapper?
    private var zone: UnsafeMutablePointer<malloc_zone_t>?

    override func tearDown() {
        mapper?.stop()
        if let zone {
            malloc_destroy_zone(zone)
        }
        zone = nil
        buffers.forEach { free($0) }
        buffers = []
        retained = []
        genericArrays = []
        super.tearDown()
    }

    func testLargeBlockIsEnumerated() throws {
        givenMallocBlock(size: 8 << 20)
        whenCapturing()
        try thenLargestBlockIsAtLeast(8 << 20)
    }

    func testInstancesOfAKnownClassAreCountedExactly() throws {
        givenProbeInstances(4_000)
        whenCapturing()
        try thenTypeRow(matching: { $0.hasSuffix(".HeapCensusProbe") }, hasBlocks: 4_000)
    }

    func testSwiftGenericInstantiationsAreNamed() throws {
        givenGenericArrays(500)
        whenCapturing()
        try thenTypeRowExists { $0.contains("_ContiguousArrayStorage<") && $0.contains("HeapCensusElement") }
    }

    func testGenericNamesStayMangledWhenDemanglingIsOff() throws {
        givenGenericArrays(500)
        whenCapturing(demangle: false)
        try thenTypeRowExists { $0.hasPrefix("_TtGC") && $0.contains("HeapCensusElement") }
    }

    func testZeroBudgetIsReportedAsTruncated() throws {
        whenCapturing(budgetMs: 0)
        try thenCensusIsTruncated()
        try thenNoZoneWasLocked()
    }

    func testBufferIsAttributedToTheObjectHoldingIt() throws {
        givenOwnerInstances(200)
        whenCapturing()
        try thenOwnerRow(matching: { $0.hasSuffix(".HeapCensusOwner") },
                         ownsAtLeast: 200, bytes: 200 * HeapCensusOwner.bufferSize)
    }

    func testOwnedBytesAreASubsetOfUnclassifiedBytes() throws {
        givenOwnerInstances(200)
        whenCapturing()
        try thenOwnedBytesAreWithinUnclassifiedBytes()
    }

    func testOwnersAreNotAttributedWhenDisabled() throws {
        givenOwnerInstances(200)
        whenCapturing(owners: false)
        try thenNothingIsOwned()
    }

    func testWebKitZoneIsNotLockedByDefault() throws {
        givenZone(named: HeapCensus.webKitZoneName, blocks: 100)
        whenCapturing()
        try thenZone(named: HeapCensus.webKitZoneName, isSkipped: true)
    }

    func testWebKitZoneIsEnumeratedWhenRequested() throws {
        givenZone(named: HeapCensus.webKitZoneName, blocks: 100)
        whenCapturing(webkit: true)
        try thenZone(named: HeapCensus.webKitZoneName, isSkipped: false)
    }

    func testCapturesSurviveRegionsBeingMappedAndUnmapped() throws {
        givenRegionsBeingMappedAndUnmapped(size: 48 << 20)
        for _ in 0..<5 {
            whenCapturing()
        }
        try thenEveryCaptureRecordedBlocks(count: 5)
    }

    func testCandidateUnmappedBeforeResolutionStaysUnresolved() throws {
        whenRunningCensusWithCandidatesUnmappedBeforeResolution(blocks: 200)
        try thenUnreadableCandidatesAreReported()
    }
}

private extension HeapCensusTests {
    func givenMallocBlock(size: Int) {
        guard let buffer = malloc(size) else {
            return XCTFail("malloc failed")
        }
        memset(buffer, 0x5A, size)
        buffers.append(buffer)
    }

    func givenProbeInstances(_ count: Int) {
        retained = (0..<count).map { _ in HeapCensusProbe() }
    }

    func givenGenericArrays(_ count: Int) {
        genericArrays = (0..<count).map { index in [HeapCensusElement(first: index, second: index)] }
    }

    func givenOwnerInstances(_ count: Int) {
        retained = (0..<count).map { _ in HeapCensusOwner() }
    }

    func givenZone(named name: String, blocks: Int) {
        guard let zone = malloc_create_zone(0, 0) else {
            return XCTFail("malloc_create_zone failed")
        }
        malloc_set_zone_name(zone, name)
        for _ in 0..<blocks {
            _ = malloc_zone_malloc(zone, 128)
        }
        self.zone = zone
    }

    func givenRegionsBeingMappedAndUnmapped(size: Int) {
        mapper = RegionMapper(size: size)
        mapper?.start()
    }

    func whenCapturing(budgetMs: UInt64 = 1_000, owners: Bool = true, webkit: Bool = false, demangle: Bool = true) {
        do {
            let options = MemoryDumpRequest.HeapOptions(
                budgetMs: budgetMs,
                limit: 100_000,
                owners: owners,
                webkit: webkit
            )
            sections.append(try HeapCensus.capture(options, demangle: demangle))
        } catch {
            XCTFail("heap census failed: \(error)")
        }
    }

    func whenRunningCensusWithCandidatesUnmappedBeforeResolution(blocks: Int) {
        let size = Int(vm_page_size)
        guard let region = mmap(nil, size, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0),
              region != MAP_FAILED
        else {
            return XCTFail("mmap failed: errno \(errno)")
        }
        for _ in 0..<blocks {
            guard let block = malloc(64) else {
                return XCTFail("malloc failed")
            }
            block.storeBytes(of: UInt(bitPattern: region), as: UInt.self)
            buffers.append(block)
        }

        let unmap = UnmapOnResolve(region: region, size: size)
        var options = bdmem_census_options_t()
        options.budget_ms = 1_000
        options.before_resolve = { context in
            Unmanaged<UnmapOnResolve>.fromOpaque(context!).takeUnretainedValue().unmap()
        }
        options.before_resolve_context = Unmanaged.passUnretained(unmap).toOpaque()

        withExtendedLifetime(unmap) {
            guard let census = bdmem_census_create(&options) else {
                return XCTFail("census creation failed")
            }
            defer { bdmem_census_destroy(census) }
            XCTAssertTrue(bdmem_census_run(census))
            summary = bdmem_census_summary(census)?.pointee
        }
        XCTAssertTrue(unmap.didUnmap)
    }

    func thenLargestBlockIsAtLeast(_ bytes: Int) throws {
        let section = try XCTUnwrap(sections.last)
        XCTAssertFalse(section.truncated)
        XCTAssertGreaterThanOrEqual(section.largestBlockBytes, UInt64(bytes))
    }

    func thenTypeRow(matching predicate: (String) -> Bool, hasBlocks blocks: UInt64) throws {
        let section = try XCTUnwrap(sections.last)
        XCTAssertFalse(section.truncated)
        let row = try XCTUnwrap(section.byType.first { predicate($0.name) })
        XCTAssertEqual(row.blocks, blocks)
        XCTAssertEqual(row.kind, .class)
    }

    func thenTypeRowExists(matching predicate: (String) -> Bool) throws {
        let section = try XCTUnwrap(sections.last)
        XCTAssertFalse(section.truncated)
        XCTAssertTrue(
            section.byType.contains { predicate($0.name) },
            "no matching type among \(section.byType.count) rows"
        )
    }

    func thenCensusIsTruncated() throws {
        let section = try XCTUnwrap(sections.last)
        XCTAssertTrue(section.truncated)
    }

    func thenNoZoneWasLocked() throws {
        let section = try XCTUnwrap(sections.last)
        XCTAssertFalse(section.zones.isEmpty)
        XCTAssertFalse(section.zones.contains(where: \.enumerated))
        XCTAssertEqual(section.totalBlocks, 0)
    }

    func thenOwnerRow(matching predicate: (String) -> Bool, ownsAtLeast blocks: UInt64, bytes: Int) throws {
        let section = try XCTUnwrap(sections.last)
        let row = try XCTUnwrap(section.byOwner.first { predicate($0.name) })
        XCTAssertGreaterThanOrEqual(row.blocks, blocks)
        XCTAssertGreaterThanOrEqual(row.bytes, UInt64(bytes))
    }

    func thenOwnedBytesAreWithinUnclassifiedBytes() throws {
        let section = try XCTUnwrap(sections.last)
        XCTAssertGreaterThan(section.ownedBytes, 0)
        XCTAssertLessThanOrEqual(section.ownedBytes, section.unclassifiedBytes)
        XCTAssertLessThanOrEqual(section.ownedBlocks, section.unclassifiedBlocks)
    }

    func thenNothingIsOwned() throws {
        let section = try XCTUnwrap(sections.last)
        XCTAssertEqual(section.ownedBlocks, 0)
        XCTAssertTrue(section.byOwner.isEmpty)
    }

    func thenZone(named name: String, isSkipped skipped: Bool) throws {
        let section = try XCTUnwrap(sections.last)
        let zone = try XCTUnwrap(section.zones.first { $0.name == name })
        XCTAssertEqual(zone.skipped, skipped)
        XCTAssertEqual(zone.enumerated, !skipped)
        XCTAssertEqual(section.zonesSkipped, skipped ? [name] : [])
        if skipped {
            XCTAssertEqual(zone.blocks, 0)
            XCTAssertEqual(zone.lockHeldMs, 0)
        } else {
            XCTAssertGreaterThanOrEqual(zone.blocks, 100)
        }
    }

    func thenEveryCaptureRecordedBlocks(count: Int) throws {
        XCTAssertEqual(sections.count, count)
        for section in sections {
            XCTAssertGreaterThan(section.totalBlocks, 0)
        }
    }

    func thenUnreadableCandidatesAreReported() throws {
        let summary = try XCTUnwrap(summary)
        XCTAssertGreaterThanOrEqual(summary.unreadable_candidates, 1)
        XCTAssertGreaterThan(summary.unclassified_blocks, 0)
    }
}

private final class UnmapOnResolve {
    private let region: UnsafeMutableRawPointer
    private let size: Int
    private(set) var didUnmap = false

    init(region: UnsafeMutableRawPointer, size: Int) {
        self.region = region
        self.size = size
    }

    func unmap() {
        didUnmap = munmap(region, size) == 0
    }
}

/// Maps, dirties and unmaps a region in a loop on a background thread.
private final class RegionMapper: @unchecked Sendable {
    private let size: Int
    private let lock = NSLock()
    private var running = false
    private let finished = DispatchSemaphore(value: 0)

    init(size: Int) {
        self.size = size
    }

    private var isRunning: Bool {
        get {
            lock.lock()
            defer { lock.unlock() }
            return running
        }
        set {
            lock.lock()
            running = newValue
            lock.unlock()
        }
    }

    func start() {
        isRunning = true
        Thread.detachNewThread { [self] in
            while isRunning {
                guard let region = mmap(nil, size, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0),
                      region != MAP_FAILED
                else {
                    continue
                }
                memset(region, 0x5A, size)
                munmap(region, size)
            }
            finished.signal()
        }
    }

    func stop() {
        isRunning = false
        finished.wait()
    }
}
