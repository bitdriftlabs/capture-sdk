// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Progress indicator for crash reporter initialization
enum CrashReporterInitState: Equatable {
    case notInitialized
    case initializing
    case initialized(ReporterInitResolution)
}

/// Final state of an initialized crash reporter
enum ReporterInitResolution: Equatable, Error {
    case sent
    case withoutPriorCrash
    case missingConfigFile
    case malformedConfigFile
    case processingFailure(String)
}

typealias CrashReporterInitResult = (CrashReporterInitState, TimeInterval)

func measureTime<T>(operation: () -> T) -> (T, TimeInterval) {
    let start = DispatchTime.now()
    let result = operation()
    let end = DispatchTime.now()
    let duration = Double(end.uptimeNanoseconds - start.uptimeNanoseconds) / Double(NSEC_PER_SEC)
    return (result, duration)
}

struct CrashReporter {
    static func processFiles() -> CrashReporterInitResult {
        return measureTime {
            switch readAndVerifyConfig() {
            case .success(let (config, destDir)):
                return copyFiles(config: config, destDir: destDir)
            case .failure(let resolution):
                return .initialized(resolution)
            }
        }
    }

    private static func getCacheDir() -> URL? {
        #if os(tvOS)
            let dirType: FileManager.SearchPathDirectory = .cachesDirectory // only caches is writeable on tvOS
        #else
            let dirType: FileManager.SearchPathDirectory = .applicationSupportDirectory
        #endif
        if let cacheDir = try? FileManager.default.url(for: dirType, in: .userDomainMask, appropriateFor: nil, create: false) {
            return cacheDir
        }
        return nil
    }

    /// Read the upload destrination and contents of the report configuration, returning both
    private static func readAndVerifyConfig() -> Result<(CrashReporterConfig, URL), ReporterInitResolution> {
        guard let configPath = Logger.reportConfigPath(),
              let destDir = Logger.reportCollectionDirectory(),
              let data = FileManager.default.contents(atPath: configPath.path)
        else {
            return .failure(.missingConfigFile)
        }

        guard let contents = String(data: data, encoding: .utf8),
              let config = CrashReporterConfig.from(contents)
        else {
            return .failure(.malformedConfigFile)
        }

        do {
            try FileManager.default.createDirectory(at: destDir, withIntermediateDirectories: true)
        } catch {
            return .failure(.processingFailure("failed to create upload directory: \(error)"))
        }

        return .success((config, destDir))
    }

    /// Ensure a URL entry is a valid report by checking it is a regular file with a predetermined file extension
    private static func isReportURL(_ entry: URL, fileExtension: String) -> Bool {
        if let properties = try? entry.resourceValues(forKeys: [.nameKey, .isRegularFileKey]),
           let name = properties.name,
           let isFile = properties.isRegularFile {
           return isFile && name.hasSuffix(".\(fileExtension)")
        }
        return false
    }

    /// Copy the first file matching the config to the destination directory
    private static func copyFiles(config: CrashReporterConfig, destDir: URL) -> CrashReporterInitState {
        let resourceNames: [URLResourceKey] = [.nameKey, .isRegularFileKey, .contentModificationDateKey]
        guard let sourceDir = getCacheDir()?.appendingPathComponent(config.rootDir),
              let files = try? FileManager.default.contentsOfDirectory(at: sourceDir, includingPropertiesForKeys: resourceNames),
              let entry = files.filter({ entry in
                  return isReportURL(entry, fileExtension: config.fileExtension)
              }).first
        else {
            return .initialized(.withoutPriorCrash)
        }
        let dest = destDir.appendingPathComponent(getUploadFilename(entry))
        do {
            try FileManager.default.copyItem(at: entry, to: dest)
            return .initialized(.sent)
        } catch {
            return .initialized(.processingFailure("failed to copy report: \(error)"))
        }
    }

    /// Create upload filename for a given URL
    private static func getUploadFilename(_ entry: URL) -> String {
        if let properties = try? entry.resourceValues(forKeys: [.contentModificationDateKey]),
           let modDate = properties.contentModificationDate {
            let modDateMillis = UInt64(modDate.timeIntervalSince1970 * 1000)
            return "\(modDateMillis)_\(entry.lastPathComponent)"
        }
        return entry.lastPathComponent
    }
}

struct CrashReporterConfig {
    let rootDir: String
    let fileExtension: String

    static func from(_ text: String) -> CrashReporterConfig? {
        let components = text.split(separator: ",", maxSplits: 1)
        guard components.count == 2
        else {
            return nil
        }
        return CrashReporterConfig(
            rootDir: String(components[0]),
            fileExtension: String(components[1]))
    }
}

extension CrashReporterInitState: CustomStringConvertible {
    var description: String {
        switch self {
        case .notInitialized:
            return "NOT_INITIALIZED"
        case .initializing:
            return "INITIALIZING"
        case .initialized(let resolution):
            switch resolution {
            case .sent:
                return "CRASH_REPORT_SENT"
            case .withoutPriorCrash:
                return "NO_PRIOR_CRASHES"
            case .missingConfigFile:
                return "MISSING_CRASH_CONFIG_FILE"
            case .malformedConfigFile:
                return "MALFORMED_CRASH_CONFIG_FILE"
            case .processingFailure:
                return "CRASH_PROCESSING_FAILURE"
            }
        }
    }
}
