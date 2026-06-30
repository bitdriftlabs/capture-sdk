// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import CaptureLoggerBridge
import Foundation
import XCTest

final class PreviousRunInfoRepositoryTests: XCTestCase {
    private var directoryURL: URL!
    private var sut: BDPreviousRunInfoRepository!
    private var previousRunInfo: BDPreviousRunInfoSnapshot?

    private let appVersion = "1.2.3"
    private let osVersion = "18.0"
    private let binaryUUID = "4f179445-15d8-4ec1-a86f-0dfe9d2bb425"
    private let bootTime: UInt64 = 123_456_789

    override func setUp() {
        directoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        previousRunInfo = nil
    }

    override func tearDown() {
        sut = nil
        try? FileManager.default.removeItem(at: directoryURL)
    }

    func testOnLoadPreviousRunInfoWithoutExistingStateReturnsNil() throws {
        givenRepository()
        try whenLoadingPreviousRunInfo()
        thenPreviousRunInfoIsNil()
    }

    func testOnLoadPreviousRunInfoWithPersistedUncleanExitReturnsSnapshot() throws {
        try givenPersistedPreviousRunInfo(wasCleanExit: false)
        try whenLoadingPreviousRunInfo()
        try thenPreviousRunInfoMatchesPreparedState(wasCleanExit: false)
    }

    func testOnLoadPreviousRunInfoWithPersistedCleanExitReturnsSnapshot() throws {
        try givenPersistedPreviousRunInfo(wasCleanExit: true)
        try whenLoadingPreviousRunInfo()
        try thenPreviousRunInfoMatchesPreparedState(wasCleanExit: true)
    }

    func testOnPrepareCurrentRunInfoWithoutTerminatingPersistsUncleanExit() throws {
        givenRepository()
        try whenPreparingCurrentRunInfo()
        try whenLoadingPersistedPreviousRunInfoFromFreshRepository()
        try thenPreviousRunInfoMatchesPreparedState(wasCleanExit: false)
    }

    func testOnMarkTerminatingPersistsCleanExitForNextLaunch() throws {
        givenRepository()
        try whenPreparingCurrentRunInfo()
        try whenMarkingTerminating()
        try whenLoadingPersistedPreviousRunInfoFromFreshRepository()
        try thenPreviousRunInfoMatchesPreparedState(wasCleanExit: true)
    }

    func testOnPrepareCurrentRunInfoWhenInvokedTwiceReturnsYes() throws {
        givenRepository()
        try whenPreparingCurrentRunInfo()
        try whenPreparingCurrentRunInfo()
        thenPrepareCurrentRunInfoSucceeds()
    }

    func testOnMarkTerminatingBeforePrepareDoesNotPersistCleanExit() throws {
        givenRepository()
        try whenMarkingTerminating()
        try whenPreparingCurrentRunInfo()
        try whenLoadingPersistedPreviousRunInfoFromFreshRepository()
        try thenPreviousRunInfoMatchesPreparedState(wasCleanExit: false)
    }

    func testOnLoadPreviousRunInfoWithCorruptedFileReturnsNil() throws {
        try givenCorruptedPreviousRunInfo()
        try whenLoadingPreviousRunInfo()
        thenPreviousRunInfoIsNil()
    }

    func testOnLoadPreviousRunInfoCachesSnapshot() throws {
        try givenPersistedPreviousRunInfo(wasCleanExit: true)
        try whenLoadingPreviousRunInfo()
        try givenRepositoryFileContainsCorruptedData()
        try whenLoadingPreviousRunInfo()
        try thenPreviousRunInfoMatchesPreparedState(wasCleanExit: true)
    }

    func testOnConcurrentLoadsReturnsConsistentSnapshot() throws {
        try givenPersistedPreviousRunInfo(wasCleanExit: true)
        let snapshots = try whenLoadingPreviousRunInfoConcurrently(iterations: 32)
        thenConcurrentLoadsReturnExpectedNumberOfSnapshots(snapshots, expectedCount: 32)
        snapshots.forEach { snapshot in
            thenSnapshotMatchesPreparedState(snapshot, wasCleanExit: true)
        }
    }

    func testOnConcurrentPrepareMarkTerminatingAndLoadIsThreadSafe() throws {
        givenRepository()
        let snapshots = try whenExercisingRepositoryConcurrently(iterations: 96)
        thenConcurrentSnapshotsAreInternallyConsistent(snapshots)
    }

    func testOnLoadPreviousRunInfoWithTruncatedFileReturnsNil() throws {
        try givenTruncatedPreviousRunInfo()
        try whenLoadingPreviousRunInfo()
        thenPreviousRunInfoIsNil()
    }

    func testOnLoadPreviousRunInfoWithOversizedFileReturnsNil() throws {
        try givenOversizedPreviousRunInfo()
        try whenLoadingPreviousRunInfo()
        thenPreviousRunInfoIsNil()
    }

    func testOnLoadPreviousRunInfoWithWrongVersionReturnsNil() throws {
        try givenPersistedPreviousRunInfo(version: 2)
        try whenLoadingPreviousRunInfo()
        thenPreviousRunInfoIsNil()
    }

    func testOnLoadPreviousRunInfoWithUninitializedRecordReturnsNil() throws {
        try givenPersistedPreviousRunInfo(isInitialized: false)
        try whenLoadingPreviousRunInfo()
        thenPreviousRunInfoIsNil()
    }

    func testOnPrepareCurrentRunInfoTruncatesStringsExceedingCapacity() throws {
        givenRepository()
        try whenPreparingCurrentRunInfo(appVersion: String(repeating: "a", count: 100))
        try whenLoadingPersistedPreviousRunInfoFromFreshRepository()
        // Capacity is 64 bytes including the null terminator, so 63 characters survive.
        try thenPreviousRunInfoAppVersion(equals: String(repeating: "a", count: 63))
    }
}

private extension PreviousRunInfoRepositoryTests {
    enum TestError: Error {
        case repositoryUnavailable
    }

    var repositoryFileURL: URL {
        directoryURL.appendingPathComponent("previous_run_info.bin")
    }

    func givenRepository() {
        sut = try? BDPreviousRunInfoRepository(directory: directoryURL)
    }

    func givenPersistedPreviousRunInfo(
        wasCleanExit: Bool = false,
        version: UInt32 = 1,
        isInitialized: Bool = true
    ) throws {
        try writePreviousRunInfoFile(makePreviousRunInfoData(
            wasCleanExit: wasCleanExit,
            version: version,
            isInitialized: isInitialized
        ))
    }

    func givenCorruptedPreviousRunInfo() throws {
        try writePreviousRunInfoFile(Data(repeating: 0, count: 192))
    }

    func givenTruncatedPreviousRunInfo() throws {
        try writePreviousRunInfoFile(Data(repeating: 0xFF, count: 10))
    }

    func givenOversizedPreviousRunInfo() throws {
        var data = makePreviousRunInfoData(wasCleanExit: false)
        data.append(0)
        try writePreviousRunInfoFile(data)
    }

    func writePreviousRunInfoFile(_ data: Data) throws {
        try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        try data.write(to: repositoryFileURL)
        givenRepository()
    }

    func givenRepositoryFileContainsCorruptedData() throws {
        try Data(repeating: 0, count: 192).write(to: repositoryFileURL)
    }

    func whenPreparingCurrentRunInfo(appVersion: String? = nil) throws {
        guard let sut else {
            throw TestError.repositoryUnavailable
        }

        try sut.prepareCurrentRunInfo(
            withAppVersion: appVersion ?? self.appVersion,
            osVersion: osVersion,
            binaryUUID: binaryUUID,
            bootTime: bootTime
        )
    }

    func whenMarkingTerminating() throws {
        guard let sut else {
            throw TestError.repositoryUnavailable
        }

        sut.markTerminating()
    }

    func whenLoadingPreviousRunInfo() throws {
        guard let sut else {
            throw TestError.repositoryUnavailable
        }

        previousRunInfo = try? sut.loadPreviousRunInfo()
    }

    func whenLoadingPersistedPreviousRunInfoFromFreshRepository() throws {
        sut = try BDPreviousRunInfoRepository(directory: directoryURL)
        try whenLoadingPreviousRunInfo()
    }

    func whenLoadingPreviousRunInfoConcurrently(iterations: Int) throws -> [BDPreviousRunInfoSnapshot] {
        guard let sut else {
            throw TestError.repositoryUnavailable
        }

        let group = DispatchGroup()
        let queue = DispatchQueue(label: "PreviousRunInfoRepositoryTests", attributes: .concurrent)
        let lock = NSLock()
        var snapshots: [BDPreviousRunInfoSnapshot] = []
        var thrownError: Error?

        for _ in 0 ..< iterations {
            group.enter()
            queue.async {
                defer { group.leave() }
                do {
                    let snapshot = try sut.loadPreviousRunInfo()
                    lock.lock()
                    snapshots.append(snapshot)
                    lock.unlock()
                } catch {
                    lock.lock()
                    thrownError = error
                    lock.unlock()
                }
            }
        }

        group.wait()

        if let thrownError {
            throw thrownError
        }

        return snapshots
    }

    func whenExercisingRepositoryConcurrently(iterations: Int) throws -> [BDPreviousRunInfoSnapshot] {
        guard let sut else {
            throw TestError.repositoryUnavailable
        }

        let lock = NSLock()
        var snapshots: [BDPreviousRunInfoSnapshot] = []
        var thrownError: Error?

        DispatchQueue.concurrentPerform(iterations: iterations) { index in
            switch index % 3 {
            case 0:
                do {
                    try sut.prepareCurrentRunInfo(
                        withAppVersion: self.appVersion,
                        osVersion: self.osVersion,
                        binaryUUID: self.binaryUUID,
                        bootTime: self.bootTime
                    )
                } catch {
                    lock.lock()
                    thrownError = error
                    lock.unlock()
                }
            case 1:
                // `markTerminating` is a no-op until `prepare` has mapped the record.
                sut.markTerminating()
            default:
                // `load` throws when no valid record is visible yet; only collect real snapshots.
                if let snapshot = try? sut.loadPreviousRunInfo() {
                    lock.lock()
                    snapshots.append(snapshot)
                    lock.unlock()
                }
            }
        }

        if let thrownError {
            throw thrownError
        }

        return snapshots
    }

    func thenPrepareCurrentRunInfoSucceeds() {
        XCTAssertNotNil(sut)
    }

    func thenPreviousRunInfoIsNil() {
        XCTAssertNil(previousRunInfo)
    }

    func thenPreviousRunInfoMatchesPreparedState(wasCleanExit: Bool) throws {
        let snapshot = try XCTUnwrap(previousRunInfo)
        thenSnapshotMatchesPreparedState(snapshot, wasCleanExit: wasCleanExit)
    }

    func thenSnapshotMatchesPreparedState(
        _ snapshot: BDPreviousRunInfoSnapshot,
        wasCleanExit: Bool
    ) {
        XCTAssertEqual(snapshot.appVersion, appVersion)
        XCTAssertEqual(snapshot.osVersion, osVersion)
        XCTAssertEqual(snapshot.binaryUUID, binaryUUID)
        XCTAssertEqual(snapshot.bootTime, bootTime)
        XCTAssertEqual(snapshot.wasCleanExit, wasCleanExit)
    }

    func thenConcurrentLoadsReturnExpectedNumberOfSnapshots(
        _ snapshots: [BDPreviousRunInfoSnapshot],
        expectedCount: Int
    ) {
        XCTAssertEqual(snapshots.count, expectedCount)
    }

    func thenConcurrentSnapshotsAreInternallyConsistent(_ snapshots: [BDPreviousRunInfoSnapshot]) {
        snapshots.forEach { snapshot in
            XCTAssertEqual(snapshot.appVersion, appVersion)
            XCTAssertEqual(snapshot.osVersion, osVersion)
            XCTAssertEqual(snapshot.binaryUUID, binaryUUID)
            XCTAssertEqual(snapshot.bootTime, bootTime)
        }
    }

    func thenPreviousRunInfoAppVersion(equals expected: String) throws {
        let snapshot = try XCTUnwrap(previousRunInfo)
        XCTAssertEqual(snapshot.appVersion, expected)
    }

    /// Builds a raw fixture matching `BDPreviousRunInfoRecord` byte-for-byte.
    ///
    /// Layout:
    /// - `uint32_t version`
    /// - `uint8_t is_terminating`
    /// - 3 bytes reserved padding
    /// - `uint64_t boot_time`
    /// - `char app_version[64]`
    /// - `char os_version[64]`
    /// - `char binary_uuid[40]`
    /// - `uint8_t is_initialized`
    /// - 7 bytes trailing padding
    ///
    /// Integer fields are encoded as little-endian to match the in-memory C struct
    /// layout read by the repository on iOS.
    func makePreviousRunInfoData(
        wasCleanExit: Bool,
        version: UInt32 = 1,
        isInitialized: Bool = true
    ) -> Data {
        var data = Data()
        data.append(version.littleEndianData)
        data.append(wasCleanExit ? 1 : 0)
        data.append(contentsOf: [0, 0, 0])
        data.append(bootTime.littleEndianData)
        data.append(fixedWidthStringData(appVersion, capacity: 64))
        data.append(fixedWidthStringData(osVersion, capacity: 64))
        data.append(fixedWidthStringData(binaryUUID, capacity: 40))
        data.append(isInitialized ? 1 : 0)
        data.append(contentsOf: Array(repeating: 0, count: 7))
        return data
    }

    func fixedWidthStringData(_ string: String, capacity: Int) -> Data {
        var data = Data(count: capacity)
        let utf8 = Array(string.utf8.prefix(capacity - 1))
        data.replaceSubrange(0 ..< utf8.count, with: utf8)
        return data
    }
}

private extension FixedWidthInteger {
    var littleEndianData: Data {
        var value = self.littleEndian
        return Data(bytes: &value, count: MemoryLayout<Self>.size)
    }
}
