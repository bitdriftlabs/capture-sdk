// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import XCTest

final class StorageLabStoreTests: XCTestCase {
    private let store = StorageLabStore()

    func testTypedValuesAreVisibleAfterRemoteMutation() throws {
        let suite = "StorageLabTests.\(UUID().uuidString)"
        defer { UserDefaults.standard.removePersistentDomain(forName: suite) }
        try self.store.setDefault(key: "enabled", text: "true", type: .bool, suite: suite)
        try self.store.setDefault(key: "count", text: "42", type: .integer, suite: suite)
        try self.store.setDefault(key: "name", text: "hello", type: .string, suite: suite)
        try self.store.setDefault(key: "ratio", text: "1.5", type: .double, suite: suite)
        try self.store.setDefault(key: "options", text: "{\"tags\":[\"ios\"]}", type: .json, suite: suite)
        let values = self.store.values(suite: suite)
        XCTAssertEqual(values["enabled"] as? Bool, true)
        XCTAssertEqual(values["count"] as? Int, 42)
        XCTAssertEqual(values["name"] as? String, "hello")
        XCTAssertEqual(values["ratio"] as? Double, 1.5)
        XCTAssertEqual((values["options"] as? [String: [String]])?["tags"], ["ios"])
        // Mirror the built-in command operating independently of the lab.
        let remote = try XCTUnwrap(UserDefaults(suiteName: suite))
        remote.set(99, forKey: "count")
        remote.removeObject(forKey: "enabled")
        XCTAssertEqual(self.store.values(suite: suite)["count"] as? Int, 99)
        XCTAssertNil(self.store.values(suite: suite)["enabled"])
        UserDefaults.standard.removePersistentDomain(forName: suite)
        XCTAssertTrue(self.store.values(suite: suite).isEmpty)
    }

    func testInvalidValuesAreRejected() {
        XCTAssertThrowsError(try StorageLabStore.ValueType.integer.parse("1.5"))
        XCTAssertThrowsError(try StorageLabStore.ValueType.double.parse("nan"))
        XCTAssertThrowsError(try StorageLabStore.ValueType.bool.parse("yes"))
        XCTAssertThrowsError(try StorageLabStore.ValueType.json.parse("{\"key\":null}"))
    }

    func testFileSizesAndRemoteDeletion() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        try self.store.create(root: root, path: "nested/example.txt", sizeKiB: 4, directory: false)
        let entries = try self.store.entries(root: root)
        XCTAssertEqual(entries.map(\.path), ["nested", "nested/example.txt"])
        XCTAssertEqual(entries.last?.bytes, 4096)
        XCTAssertThrowsError(try self.store.create(root: root, path: "nested/example.txt",
                                                   sizeKiB: 4, directory: false))
        try self.store.delete(root: root, path: "nested")
        XCTAssertTrue(try self.store.entries(root: root).isEmpty)
        try FileManager.default.removeItem(at: root)
        XCTAssertTrue(try self.store.entries(root: root).isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.path))
    }

    func testPathsCannotEscapeLabThroughTraversalOrSymlinks() throws {
        let parent = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let root = parent.appendingPathComponent("lab")
        let outside = parent.appendingPathComponent("outside")
        defer { try? FileManager.default.removeItem(at: parent) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: outside, withIntermediateDirectories: true)
        try FileManager.default.createSymbolicLink(at: root.appendingPathComponent("link"),
                                                   withDestinationURL: outside)
        for path in ["", "/absolute", "..", "../outside/file", "nested/../../outside", "link/file"] {
            XCTAssertThrowsError(try self.store.create(root: root, path: path, sizeKiB: 1, directory: false))
            XCTAssertThrowsError(try self.store.delete(root: root, path: path))
        }
        XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: outside.path).isEmpty)
    }
}
