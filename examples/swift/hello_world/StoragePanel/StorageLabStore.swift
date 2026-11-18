// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct StorageLabStore {
    static let folderName = "StorageLab"
    static let testSuite = "io.bitdrift.demo.storage-lab"

    enum Directory: String, CaseIterable, Identifiable {
        case caches = "Caches"
        case documents = "Documents"
        case applicationSupport = "Application Support"
        case temporary = "Temporary"

        var id: String { self.rawValue }
    }

    enum ValueType: String, CaseIterable, Identifiable {
        case string = "String"
        case integer = "Integer"
        case double = "Double"
        case bool = "Bool"
        case json = "JSON array / object"

        var id: String { self.rawValue }

        func parse(_ text: String) throws -> Any {
            switch self {
            case .string:
                return text
            case .integer:
                guard let value = Int(text) else { throw LabError("Enter a valid integer.") }
                return value
            case .double:
                guard let value = Double(text), value.isFinite else {
                    throw LabError("Enter a finite number.")
                }
                return value
            case .bool:
                guard ["true", "false"].contains(text.lowercased()) else {
                    throw LabError("Enter true or false.")
                }
                return text.lowercased() == "true"
            case .json:
                let value = try JSONSerialization.jsonObject(with: Data(text.utf8))
                guard PropertyListSerialization.propertyList(value, isValidFor: .binary) else {
                    throw LabError("UserDefaults arrays and objects cannot contain null.")
                }
                return value
            }
        }
    }

    struct LabError: LocalizedError {
        let errorDescription: String?

        init(_ message: String) { self.errorDescription = message }
    }

    struct Entry: Identifiable {
        let path: String
        let isDirectory: Bool
        let bytes: Int
        var id: String { self.path }
    }

    let fileManager: FileManager

    init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
    }

    func defaults(suite: String) throws -> UserDefaults {
        if suite.isEmpty {
            return .standard
        }
        guard let defaults = UserDefaults(suiteName: suite) else {
            throw LabError("Could not open the selected suite.")
        }
        return defaults
    }

    func values(suite: String) -> [String: Any] {
        let domain = suite.isEmpty ? Bundle.main.bundleIdentifier : suite
        guard let domain else {
            return [:]
        }
        return UserDefaults.standard.persistentDomain(forName: domain) ?? [:]
    }

    func setDefault(key: String, text: String, type: ValueType, suite: String) throws {
        guard !key.isEmpty else { throw LabError("Enter a key.") }
        try self.defaults(suite: suite).set(type.parse(text), forKey: key)
    }

    func root(for directory: Directory) throws -> URL {
        let base: URL
        switch directory {
        case .caches:
            base = try self.fileManager.url(for: .cachesDirectory, in: .userDomainMask,
                                            appropriateFor: nil, create: true)
        case .documents:
            base = try self.fileManager.url(for: .documentDirectory, in: .userDomainMask,
                                            appropriateFor: nil, create: true)
        case .applicationSupport:
            base = try self.fileManager.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                            appropriateFor: nil, create: true)
        case .temporary:
            base = self.fileManager.temporaryDirectory
        }
        return base.appendingPathComponent(Self.folderName, isDirectory: true)
    }

    func entries(root: URL) throws -> [Entry] {
        // Reading never recreates the directory: remote deletion must remain visible.
        let root = root.standardizedFileURL.resolvingSymlinksInPath()
        guard self.fileManager.fileExists(atPath: root.path) else {
            return []
        }
        guard let enumerator = self.fileManager.enumerator(
            at: root, includingPropertiesForKeys: [.isDirectoryKey, .fileSizeKey, .isSymbolicLinkKey]
        ) else { throw LabError("Could not list the directory.") }
        var entries: [Entry] = []
        for case let url as URL in enumerator {
            let values = try url.resourceValues(forKeys: [.isDirectoryKey, .fileSizeKey, .isSymbolicLinkKey])
            if values.isSymbolicLink == true { continue }
            entries.append(Entry(
                path: url.pathComponents.suffix(enumerator.level).joined(separator: "/"),
                isDirectory: values.isDirectory == true,
                bytes: values.isDirectory == true ? 0 : (values.fileSize ?? 0)
            ))
        }
        return entries.sorted { $0.path < $1.path }
    }

    func create(root: URL, path: String, sizeKiB: Int, directory: Bool) throws {
        let url = try self.child(root: root, path: path)
        guard !self.fileManager.fileExists(atPath: url.path) else {
            throw LabError("That path already exists. Delete it or choose another name.")
        }
        guard (1...10_240).contains(sizeKiB) else { throw LabError("Use 1–10240 KiB.") }
        try self.fileManager.createDirectory(at: url.deletingLastPathComponent(),
                                             withIntermediateDirectories: true)
        if directory {
            try self.fileManager.createDirectory(at: url, withIntermediateDirectories: true)
        } else {
            try Data(repeating: 0x41, count: sizeKiB * 1024).write(to: url, options: .atomic)
        }
    }

    func delete(root: URL, path: String) throws {
        try self.fileManager.removeItem(at: self.child(root: root, path: path))
    }

    private func child(root: URL, path: String) throws -> URL {
        let components = path.split(separator: "/", omittingEmptySubsequences: false)
        guard !path.isEmpty, !components.contains(where: { $0.isEmpty || $0 == "." || $0 == ".." }) else {
            throw LabError("Use a relative path such as nested/sample.txt, without . or .. components.")
        }
        var url = root.standardizedFileURL.resolvingSymlinksInPath()
        for component in components {
            url.appendPathComponent(String(component))
            // Resolving a nonexistent leaf does not reliably resolve its symlinked ancestors.
            if (try? self.fileManager.destinationOfSymbolicLink(atPath: url.path)) != nil {
                throw LabError("Paths through symbolic links are not supported.")
            }
        }
        return url
    }
}
