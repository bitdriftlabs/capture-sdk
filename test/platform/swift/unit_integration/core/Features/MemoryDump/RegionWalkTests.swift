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
    private var sections: [RegionWalk.Report] = []

    override func tearDown() {
        if let mappedRegion {
            munmap(mappedRegion, regionSize)
        }
        mappedRegion = nil
        super.tearDown()
    }

    func testChargedBytesNeverExceedTheFootprint() throws {
        whenWalking()
        try thenChargedBytesAreWithinTheFootprint()
    }

    func testTaggedRegionShowsUnderItsTag() throws {
        try givenDirtyTaggedRegion()
        whenWalking()
        try thenTaggedRegionIsChargedAtLeast(regionSize)
    }

    func testSubmapsDoNotLoop() {
        whenWalking()
        thenWalkCompletedWithoutTruncation()
    }

    func testImagesAreNamedWithoutPaths() {
        whenWalking()
        thenImagesAreNamedWithoutPaths()
    }

    func testWalkWithoutImagesAttributesNothingToImages() {
        whenWalking(images: nil)
        thenNoImageIsCharged()
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

    func whenWalking(images: ImageTable? = ImageTable()) {
        do {
            sections.append(try RegionWalk.capture(images: images))
        } catch {
            XCTFail("region walk failed: \(error)")
        }
    }

    func taggedRow(in section: RegionWalk.Report) throws -> RegionWalk.TagRow {
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

    func thenNoImageIsCharged() {
        guard let section = sections.last else {
            return XCTFail("missing section")
        }
        XCTAssertGreaterThan(section.charged, 0)
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

    func thenImagesAreNamedWithoutPaths() {
        guard let section = sections.last else {
            return XCTFail("missing section")
        }
        XCTAssertFalse(section.byImage.isEmpty)
        XCTAssertFalse(section.byImage.contains { $0.name.contains("/") })
        XCTAssertEqual(section.byImage, section.byImage.sorted { $0.charged > $1.charged })
    }
}
