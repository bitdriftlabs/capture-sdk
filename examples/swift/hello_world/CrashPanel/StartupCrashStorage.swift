// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

final class StartupCrashStorage {
    private let fileURL: URL
    private let fileManager: FileManager

    init(
        fileManager: FileManager = .default,
        directory: URL? = nil
    ) {
        self.fileManager = fileManager
        let resolvedDirectory = directory
            ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        fileURL = resolvedDirectory.appendingPathComponent("hello_world_startup_crash")
    }

    func schedule(_ identifier: String) {
        do {
            try fileManager.createDirectory(
                at: fileURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try identifier.write(to: fileURL, atomically: true, encoding: .utf8)
        } catch {
            print("Failed to schedule startup crash: \(error)")
        }
    }

    func pendingIdentifier() -> String? {
        try? String(contentsOf: fileURL, encoding: .utf8)
    }

    func clear() {
        do {
            try fileManager.removeItem(at: fileURL)
        } catch {
            let nsError = error as NSError
            guard nsError.domain == NSCocoaErrorDomain, nsError.code == NSFileNoSuchFileError else {
                print("Failed to clear startup crash: \(error)")
                return
            }
        }
    }
}
