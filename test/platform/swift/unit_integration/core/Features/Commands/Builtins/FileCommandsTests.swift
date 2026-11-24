// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) @testable import Capture

import XCTest

private let mask = FileManager.SearchPathDomainMask.allDomainsMask
private let CACHE_DIRECTORY = FileManager.default.urls(for: FileManager.SearchPathDirectory.cachesDirectory, in: mask).first!
private let SAMPLE_DOCUMENT = FileManager.default.urls(for: FileManager.SearchPathDirectory.documentDirectory, in: mask).first!.appendingPathComponent("yay.txt")

class FileCommandsTestCase: XCTestCase {
    override func setUpWithError() throws {
        try FileManager.default.createDirectory(at: CACHE_DIRECTORY, withIntermediateDirectories: true)
        FileManager.default.createFile(atPath: SAMPLE_DOCUMENT.path, contents: "yay".data(using: .utf8))
    }

    override func tearDownWithError() throws {
        try FileManager.default.removeItem(at: CACHE_DIRECTORY)
        try FileManager.default.removeItem(at: SAMPLE_DOCUMENT)
    }

    fileprivate func writeCacheFile(_ path: String, sizeKb: Int = 0) throws {
        let filePath = CACHE_DIRECTORY.appendingPathComponent(path).path
        let subDir = NSURL.fileURL(withPath: NSString(string: filePath).deletingLastPathComponent)
        try FileManager.default.createDirectory(at: subDir, withIntermediateDirectories: true)
        let count = sizeKb > 0 ? 1_024 * sizeKb : 1 // at minimum make a 1-byte file
        let contents = String(repeating: "o", count: count).data(using: .utf8)
        FileManager.default.createFile(atPath: filePath, contents: contents)
    }
}

final class FileCommandsSizeTests: FileCommandsTestCase {
    private let sut = FileCommands.sizeKb

    func testSizeEmpty() throws {
        XCTAssertEqual(sut([:]), Result.success(CommandResult(context: ["file_size_kb": "0"])))
    }

    func testSizeWithContents() throws {
        try writeCacheFile("sample1.txt", sizeKb: 3)
        try writeCacheFile("sample2.txt", sizeKb: 1)

        XCTAssertEqual(sut([:]), Result.success(CommandResult(context: ["file_size_kb": "4"])))
    }

    func testSizeInvalidArguments() throws {
        XCTAssertEqual(sut(["path": 4.asArgument()]), Result.failure(CommandError.invalidArguments(message: "<path> argument must be a string")))
    }

    func testSizeMissingFile() throws {
        try writeCacheFile("sample1.txt", sizeKb: 3)
        try writeCacheFile("sample2.txt", sizeKb: 4)

        XCTAssertEqual(sut(["path": "sample3.txt".asArgument()]), Result.failure(CommandError(message: "file does not exist")))
    }

    func testSizeSpecificFile() throws {
        try writeCacheFile("sample1.txt", sizeKb: 3)
        try writeCacheFile("sample2.txt", sizeKb: 4)

        XCTAssertEqual(sut(["path": "sample1.txt".asArgument()]), Result.success(CommandResult(context: ["file_size_kb": "3"])))
    }

    func testSizeEscape() throws {
        XCTAssertEqual(sut(["path": SAMPLE_DOCUMENT.path.asArgument()]), Result.failure(CommandError(message: "file does not exist")))
    }
}

final class FileCommandsDeleteTests: FileCommandsTestCase {
    private let sut = FileCommands.delete

    override func setUpWithError() throws {
        try super.setUpWithError()
        try writeCacheFile("a/sample1.txt", sizeKb: 3)
        try writeCacheFile("a/sample2.txt", sizeKb: 3)
        try writeCacheFile("b/sample.txt", sizeKb: 1)
        try writeCacheFile("sample.txt", sizeKb: 1)
    }

    func testDeleteNonexistent() throws {
        XCTAssertEqual(sut(["path": "a/sample9.txt".asArgument()]), Result.failure(CommandError(message: "file does not exist")))
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("a/sample1.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("a/sample2.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("b/sample.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("sample.txt").path)
    }

    func testDeleteEscape() throws {
        XCTAssertEqual(sut(["path": SAMPLE_DOCUMENT.path.asArgument()]), Result.failure(CommandError(message: "file does not exist")))
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("a/sample1.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("a/sample2.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("b/sample.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("sample.txt").path)
    }

    func testDelete() throws {
        XCTAssertEqual(sut(["path": "a/sample1.txt".asArgument()]), Result.success(CommandResult()))
        AssertFileDoesNotExist(CACHE_DIRECTORY.appendingPathComponent("a/sample1.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("a/sample2.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("b/sample.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("sample.txt").path)
    }

    func testDeleteDirectoryWithoutRecursive() throws {
        XCTAssertEqual(
            sut(["path": "a".asArgument()]),
            .failure(CommandError(
                message: "did not delete file: declining to delete directory without <recursive> set to true"
            ))
        )
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("a/sample1.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("a/sample2.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("b/sample.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("sample.txt").path)
    }

    func testDeleteDirectoryWithRecursive() throws {
        XCTAssertEqual(sut(["path": "a".asArgument(), "recursive": true.asArgument()]), Result.success(CommandResult()))
        AssertFileDoesNotExist(CACHE_DIRECTORY.appendingPathComponent("a").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("b/sample.txt").path)
        AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("sample.txt").path)
    }
}

final class FileCommandsClearTests: FileCommandsTestCase {
    private let sut = FileCommands.clear

    func testClearEmpty() {
        XCTAssertEqual(sut([:]), Result.success(CommandResult()))
    }

    func testClear() throws {
        try writeCacheFile("a/sample1.txt", sizeKb: 3)
        try writeCacheFile("a/sample2.txt", sizeKb: 3)
        try writeCacheFile("b/sample.txt", sizeKb: 1)
        try writeCacheFile("sample.txt", sizeKb: 1)

        XCTAssertEqual(sut([:]), Result.success(CommandResult()))
        AssertFileDoesNotExist(CACHE_DIRECTORY.appendingPathComponent("a").path)
        AssertFileDoesNotExist(CACHE_DIRECTORY.appendingPathComponent("b").path)
        AssertFileDoesNotExist(CACHE_DIRECTORY.appendingPathComponent("sample.txt").path)
    }
}

final class FileCommandsListTests: FileCommandsTestCase {
    private let sut = FileCommands.list

    func testListInvalidArguments() throws {
        XCTAssertEqual(sut(["path": false.asArgument()]), Result.failure(CommandError.invalidArguments(message: "<path> argument must be a string")))
    }

    func testListNonexistent() throws {
        XCTAssertEqual(sut(["path": "somedir".asArgument()]), Result.failure(CommandError(message: "file does not exist")))
    }

    func testListSmall() throws {
        for fileIndex in 0..<5 {
            try writeCacheFile("somedir/sample\(fileIndex).txt")
        }
        XCTAssertEqual(sut([:]), Result.success(CommandResult(context: ["file_count": "1", "contents": "somedir"])))
        XCTAssertEqual(sut(["path": "somedir".asArgument()]), Result.success(CommandResult(context: ["file_count": "5", "contents": "sample0.txt\nsample1.txt\nsample2.txt\nsample3.txt\nsample4.txt"])))
    }

    func testListBig() throws {
        for fileIndex in 0..<200 {
            try writeCacheFile("somedir/sample\(fileIndex).txt")
        }

        let result = sut(["path": "somedir".asArgument()])
        guard case .success(let result) = result else {
            XCTFail("list command did not succeed: \(result)")
            return
        }

        XCTAssertEqual(result.context, ["file_count": "200"])
        XCTAssertNotNil(result.attachment)
        AssertAnyEqual("text/plain", result.attachment?.mimeType)

        guard let fileListData = result.attachment?.data else {
            XCTFail("list command did not attach file list")
            return
        }

        let fileList = String(data: fileListData, encoding: .utf8)!.split(separator: "\n")
        XCTAssertEqual(fileList.sorted(), fileList)
        XCTAssertEqual(200, fileList.count)
        for filePath in fileList {
            AssertFileExists(CACHE_DIRECTORY.appendingPathComponent("somedir/\(filePath)").path)
        }
    }

    func testListEscape() throws {
        let result = sut(["path": SAMPLE_DOCUMENT.path.asArgument()])
        XCTAssertEqual(result, Result.failure(CommandError(message: "file does not exist")))
    }
}
