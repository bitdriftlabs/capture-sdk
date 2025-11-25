// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

enum ReportDirectory {
    private static let fileManager = FileManager.default
    
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
    
    static func reportsDirectory(isFatal: Bool) -> URL? {
        guard let sdkDir = ensureSDKDirectory() else {
            return nil
        }
        
        let reportsDir = sdkDir.appendingPathComponent(
            isFatal ? "reports/new" : "reports/watcher/current_session",
            isDirectory: true
        )
        
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

