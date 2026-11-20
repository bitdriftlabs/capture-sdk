// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

enum ReportDirectory {
    private static let fileManager = FileManager.default
    private static let watcherDirPath = "reports/watcher"
    
    static func sdkBaseDirectory() -> URL? {
        return try? fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
    }
    
    static func ensureSDKDirectory() -> URL? {
        guard let baseDir = sdkBaseDirectory() else {
            return nil
        }
        
        let sdkDir = baseDir.appendingPathComponent("bitdrift_capture", isDirectory: true)
        
        if !fileManager.fileExists(atPath: sdkDir.path) {
            try? fileManager.createDirectory(at: sdkDir, withIntermediateDirectories: true)
        }
        
        return sdkDir
    }
    
    static func getWatcherDirectory() -> URL? {
        return ensureSDKDirectory()?.appendingPathComponent(watcherDirPath, isDirectory: true)
    }
    
    static func setupWatcherDirectory(enableJsErrors: Bool) {
        guard let sdkDir = ensureSDKDirectory() else {
            return
        }
        let watcherDir = sdkDir.appendingPathComponent(watcherDirPath, isDirectory: true)
        
        if enableJsErrors {
            if !fileManager.fileExists(atPath: watcherDir.path) {
                try? fileManager.createDirectory(at: watcherDir, withIntermediateDirectories: true)
            }
        } else {
            if fileManager.fileExists(atPath: watcherDir.path) {
                try? fileManager.removeItem(at: watcherDir)
            }
        }
    }
    
    static func reportsDirectory(isFatal: Bool) -> URL? {
        guard let sdkDir = ensureSDKDirectory() else {
            return nil
        }
        
        let reportsDir: URL
        if isFatal {
            reportsDir = sdkDir.appendingPathComponent("reports/new", isDirectory: true)
        } else if let watcherDir = getWatcherDirectory() {
            reportsDir = watcherDir.appendingPathComponent("current_session", isDirectory: true)
        } else {
            return nil
        }
        
        if !fileManager.fileExists(atPath: reportsDir.path) {
            try? fileManager.createDirectory(at: reportsDir, withIntermediateDirectories: true)
        }
        
        guard fileManager.isWritableFile(atPath: reportsDir.path) else {
            return nil
        }
        
        return reportsDir
    }
    
    static func destinationPath(isFatal: Bool) -> String? {
        guard let reportsDir = reportsDirectory(isFatal: isFatal) else {
            return nil
        }
        
        let fileName = isFatal
            ? "javascript-fatal-\(UUID().uuidString).cap"
            : "javascript-non-fatal-\(UUID().uuidString).cap"
        
        return reportsDir.appendingPathComponent(fileName).path
    }
}

