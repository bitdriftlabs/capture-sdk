// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Darwin
import XCTest

final class RegionWalkTests: XCTestCase {
    /// VM_MEMORY_APPLICATION_SPECIFIC_1, reserved for apps.
    private let applicationTag: UInt32 = 240
    private let regionSize = 8 << 20

    private var mappedRegion: UnsafeMutableRawPointer?
    private var sections: [RegionWalk.Section] = []

    override func tearDown() {
        if let mappedRegion {
            munmap(mappedRegion, regionSize)
        }
        mappedRegion = nil
        super.tearDown()
    }

    func testChargedBytesNeverExceedTheFootprint() throws {
        whenWalking(mode: .writable)
        try thenChargedBytesAreWithinTheFootprint()
    }

    func testTaggedRegionShowsUnderItsTag() throws {
        try givenDirtyTaggedRegion()
        whenWalking(mode: .writable)
        try thenTaggedRegionIsChargedAtLeast(regionSize)
    }

    func testWritableAndFullWalksAgreeOnOwnRegion() throws {
        try givenDirtyTaggedRegion()
        whenWalking(mode: .writable)
        whenWalking(mode: .full)
        try thenTaggedRegionChargesAgree(within: 0.02)
    }

    func testLayoutWalkReportsNoPageCounts() {
        whenWalking(mode: .layout)
        thenNoPagesAreCounted()
    }

    func testSubmapsDoNotLoop() {
        whenWalking(mode: .full)
        thenWalkCompletedWithoutTruncation()
    }

    func testRowsBeyondTheLimitCollapseIntoOther() throws {
        whenWalking(mode: .writable, limit: 1)
        try thenRowsCollapseIntoOther()
    }

    func testImagesAreNamedWithoutPaths() {
        whenWalking(mode: .writable)
        thenImagesAreNamedWithoutPaths()
    }
}

private extension RegionWalkTests {
    func givenDirtyTaggedRegion() throws {
        let taggedDescriptor = Int32(bitPattern: applicationTag << 24)
        let pointer = mmap(nil, regionSize, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, taggedDescriptor, 0)
        guard let pointer, pointer != MAP_FAILED else {
            throw XCTSkip("mmap failed: errno \(errno)")
        }
        memset(pointer, 0x5A, regionSize)
        mappedRegion = pointer
    }

    func whenWalking(mode: MemoryDumpRequest.RegionsMode, limit: UInt64 = 256) {
        do {
            sections.append(try RegionWalk.capture(.init(mode: mode, limit: limit)))
        } catch {
            XCTFail("region walk failed: \(error)")
        }
    }

    func taggedRow(in section: RegionWalk.Section) throws -> RegionWalk.TagRow {
        try XCTUnwrap(section.byTag.first { $0.tag == applicationTag })
    }

    func thenChargedBytesAreWithinTheFootprint() throws {
        let section = try XCTUnwrap(sections.last)
        let footprint = try XCTUnwrap(VirtualMemoryCounters.physFootprint())
        XCTAssertGreaterThan(section.charged, 0)
        XCTAssertLessThanOrEqual(section.charged, footprint)
    }

    func thenTaggedRegionIsChargedAtLeast(_ bytes: Int) throws {
        let row = try taggedRow(in: try XCTUnwrap(sections.last))
        XCTAssertGreaterThanOrEqual(row.virtualSize, UInt64(bytes))
        XCTAssertGreaterThan(row.charged, UInt64(Double(bytes) * 0.9))
    }

    func thenTaggedRegionChargesAgree(within tolerance: Double) throws {
        XCTAssertEqual(sections.count, 2)
        let writable = try taggedRow(in: sections[0]).charged
        let full = try taggedRow(in: sections[1]).charged
        let drift = Double(max(writable, full) - min(writable, full)) / Double(full)
        XCTAssertLessThanOrEqual(drift, tolerance)
    }

    func thenNoPagesAreCounted() {
        guard let section = sections.last else {
            return XCTFail("missing section")
        }
        XCTAssertEqual(section.mode, "layout")
        XCTAssertEqual(section.charged, 0)
        XCTAssertEqual(section.sharedDirty, 0)
        XCTAssertGreaterThan(section.virtualSize, 0)
        XCTAssertTrue(section.byImage.isEmpty)
    }

    func thenWalkCompletedWithoutTruncation() {
        guard let section = sections.last else {
            return XCTFail("missing section")
        }
        XCTAssertFalse(section.truncated)
        XCTAssertGreaterThan(section.regions, 0)
        XCTAssertEqual(section.byTag.reduce(0) { $0 + $1.regions }, section.regions)
    }

    func thenRowsCollapseIntoOther() throws {
        let section = try XCTUnwrap(sections.last)
        XCTAssertEqual(section.byTag.count, 2)
        let other = try XCTUnwrap(section.byTag.last)
        XCTAssertEqual(other.name, RegionWalk.otherRowName)
        XCTAssertNil(other.tag)
        XCTAssertEqual(section.byTag.reduce(0) { $0 + $1.charged }, section.charged)
        XCTAssertEqual(section.byTag.reduce(0) { $0 + $1.regions }, section.regions)
        XCTAssertLessThanOrEqual(section.byImage.count, 2)
    }

    func thenImagesAreNamedWithoutPaths() {
        guard let section = sections.last else {
            return XCTFail("missing section")
        }
        XCTAssertFalse(section.byImage.isEmpty)
        XCTAssertFalse(section.byImage.contains { $0.name.contains("/") })
        XCTAssertEqual(section.byImage, section.byImage.sorted { $0.charged > $1.charged })
    }
}
