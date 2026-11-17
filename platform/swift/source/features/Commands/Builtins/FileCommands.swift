// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

@_spi(ExperimentalAPI) public class FileCommands {
    private static var cacheDirURL: Result<URL, CommandError> {
        get {
            let target = FileManager.SearchPathDirectory.cachesDirectory
            let mask = FileManager.SearchPathDomainMask.allDomainsMask
            return switch FileManager.default.urls(for: target, in: mask).first {
            case .none: .failure(CommandError(title: "failed to access file system"))
            case .some(let url): .success(url)
            }
        }
    }

    /// Command to return the size of a file or directory in the cache. Returns the full cache size
    /// if `path` is unspecified.
    ///
    /// - parameter arguments: command arguments. optional `path` (string)
    ///
    /// - returns: a `CommandResult` containing the size in kilobytes in context or an error
    static func sizeKb(_ arguments: CommandArguments) -> Result<CommandResult, CommandError> {
        let KB: Int = 1_024

        return cacheDirURL
            .flatMap { subpath($0, arguments) }
            .flatMap { size(fileURL: $0) }
            .map { CommandResult(attachment: nil, context: ["file_size_kb": "\($0 / KB)"]) }
    }

    /// Command to return the file names in a directory in the cache. Uses the full cache size
    /// if `path` is unspecified.
    ///
    /// - parameter arguments: command arguments. optional `path` (string)
    ///
    /// - returns: a `CommandResult` containing the directory contents or an error
    static func list(_ arguments: CommandArguments) -> Result<CommandResult, CommandError> {
        /// Number of bytes which can be listed in file command context before instead using an attachment. Pure vibes, but "100 10-character names" seemed reasonable 🤷
        let LIST_CONTEXT_MAX_BYTES: Int = 1_000

        return cacheDirURL
            .flatMap { subpath($0, arguments) }
            .flatMap { fileURL in
                do {
                    let items = try FileManager.default.contentsOfDirectory(atPath: fileURL.filePath)
                    let contents =  String(items.sorted().joined(separator: "\n"))
                    var context = ["file_count": "\(items.count)"]
                    var attachment: CommandAttachment?

                    if contents.lengthOfBytes(using: .utf8) > LIST_CONTEXT_MAX_BYTES {
                        guard let data = contents.data(using: .utf8) else {
                            return .failure(CommandError(title: "failed to encode response"))
                        }
                        attachment = CommandAttachment(data: data, mimeType: "text/plain")
                    } else {
                        context["contents"] = contents
                    }
                    return .success(CommandResult(attachment: attachment, context: context))
                } catch {
                    return .failure(CommandError(title: "failed to read directory"))
                }
            }
    }

    /// Command to delete a file or directory
    ///
    /// - parameter arguments: command arguments. requires `path` (string) to be set
    ///
    /// - returns: an empty `CommandResult` or an error
    static func delete(_ arguments: CommandArguments) -> Result<CommandResult, CommandError> {
        return cacheDirURL
            .flatMap { subpath($0, arguments, requirePath: true) }
            .flatMap { fileURL in
                var isDir: ObjCBool = false
                if FileManager.default.fileExists(atPath: fileURL.filePath, isDirectory: &isDir) && isDir.boolValue {
                    guard let recursiveArg = arguments["recursive"], case .bool(let recursive) = recursiveArg, recursive else {
                        return .failure(CommandError(title: "did not delete file", description: "declining to delete directory without <recursive> set to true"))
                    }
                }
                do {
                    try FileManager.default.removeItem(at: fileURL)
                } catch {
                    return .failure(CommandError(title: "failed to delete file"))
                }
                return .success(CommandResult())
            }
    }

    /// Command to delete the contents of the directory
    ///
    /// - parameter arguments: command arguments. requires `path` (string) to be set
    ///
    /// - returns: an empty `CommandResult` or an error
    static func clear(_ arguments: CommandArguments) -> Result<CommandResult, CommandError> {
        return cacheDirURL
            .flatMap { fileEnumerator($0) }
            .flatMap { enumerator in
                for case let fileURL as URL in enumerator {
                    do {
                        try FileManager.default.removeItem(at: fileURL)
                    } catch {
                        return .failure(CommandError(title: "failed to delete file"))
                    }
                }
                return .success(CommandResult())
            }
    }
}

private let ERR_DOES_NOT_EXIST = CommandError(title: "file does not exist")

/// File size in bytes
///
/// - parameter fileURL: a URL representing a file path
///
/// - returns: the file size or an error
private func size(fileURL: URL) -> Result<Int, CommandError> {
    var totalSize = 0
    let resourceKeys = [URLResourceKey.fileSizeKey]
    return fileEnumerator(fileURL, resourceKeys: resourceKeys).flatMap { enumerator in
        for case let fileURL as URL in enumerator {
            guard let resourceValues = try? fileURL.resourceValues(forKeys: Set(resourceKeys)),
                  let fileSize = resourceValues.fileSize
            else {
                continue
            }
            totalSize += fileSize
        }
        return .success(totalSize)
    }
}

/// Read the `path` argument from `arguments` if specified, otherwise return `dir`.
/// Disallows paths outside of `dir`
///
/// - parameter dir:         a file path representing a directory
/// - parameter arguments:   command arguments to read
/// - parameter requirePath: true to require the path argument to exist
///
/// - returns: A path within `dir`, `dir` itself if `path` is unspecified, or an error
private func subpath(_ dir: URL, _ arguments: CommandArguments, requirePath: Bool = false) -> Result<URL, CommandError> {
    let PATH_REQUIRED_ERROR = CommandError.invalidArguments(description: "<path> argument must be a string")

    guard let pathArg = arguments["path"] else {
        return requirePath ? .failure(PATH_REQUIRED_ERROR) : .success(dir)
    }

    guard case .string(let path) = pathArg else {
        return .failure(PATH_REQUIRED_ERROR)
    }

    let fileURL = dir.join(path)
    var relation: FileManager.URLRelationship = .other
    try? FileManager.default.getRelationship(&relation, ofDirectoryAt: dir, toItemAt: fileURL)
    if case .other = relation {
        // disallow access outside of the cache directory and discard paths for nonexistent files
        return .failure(ERR_DOES_NOT_EXIST)
    }

    return .success(fileURL)
}

/// Enumerate over all of the files represented by a URL
///
/// - parameter fileURL:      a URL representing a file or directory
/// - parameter resourceKeys: attributes to pre-fetch for enumerated entries
///
/// - returns: an enumerator or an error if the file on disk cannot be read
private func fileEnumerator(_ fileURL: URL, resourceKeys: [URLResourceKey]? = nil) -> Result<any Sequence, CommandError> {
    var isDir: ObjCBool = false
    if !fileURL.isFileURL || !FileManager.default.fileExists(atPath: fileURL.filePath, isDirectory: &isDir) {
        return .failure(ERR_DOES_NOT_EXIST)
    }
    if isDir.boolValue {
        return switch FileManager.default.enumerator(at: fileURL, includingPropertiesForKeys: resourceKeys) {
        case .none: .failure(CommandError(title: "failed to read directory"))
        case .some(let enumerator): .success(enumerator)
        }
    }
    return .success([fileURL] as NSArray)
}
