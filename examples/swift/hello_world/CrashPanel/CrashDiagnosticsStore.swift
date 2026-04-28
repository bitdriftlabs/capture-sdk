// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import Capture

struct CrashDiagnosticsStore {
    static let defaultEncoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }()
    
    private let fileURL: URL
    private let encoder: JSONEncoder
    private let fileManager: FileManager

    init(
        fileManager: FileManager = .default,
        encoder: JSONEncoder = Self.defaultEncoder
    ) {
        self.fileManager = fileManager
        self.encoder = encoder
        let applicationSupportDirectory = try? fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        
        self.fileURL = applicationSupportDirectory!
            .appendingPathComponent(
                "hello_world_recent_crash_diagnostics.json",
                isDirectory: false
            )
        
    }

    func save(_ records: [StoredCrashDiagnostic]) {
        guard let data = try? self.encoder.encode(records) else {
            return
        }
        
        do {
            try fileManager.createDirectory(
                at: self.fileURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try data.write(to: self.fileURL, options: .atomic)
        } catch let exception {
            Logger.log(level: .error, message: "Couldn't save crash diagnostics: \(exception)")
        }
    }

    func clear() {
        do {
            try fileManager.removeItem(at: self.fileURL)
        } catch let exception {
            Logger.log(level: .error, message: "Couldn't clear crash diagnostics: \(exception)")
        }
    }
}
