// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Progress indicator for crash reporter initialization
enum IssueReporterInitState: Equatable {
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

typealias IssueReporterInitResult = (IssueReporterInitState, TimeInterval)

func measureTime<T>(operation: () -> T) -> (T, TimeInterval) {
    let start = DispatchTime.now()
    let result = operation()
    let end = DispatchTime.now()
    let duration = Double(end.uptimeNanoseconds - start.uptimeNanoseconds) / Double(NSEC_PER_SEC)
    return (result, duration)
}

struct FatalIssueReporter {
    static func processFiles() -> IssueReporterInitResult {
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
    ///
    /// - returns: the configuration fields and destination URL or an initialized state indicating the config
    ///            failure type
    private static func readAndVerifyConfig() -> Result<(IssueReporterConfig, URL), ReporterInitResolution> {
        guard let configPath = Logger.reportConfigPath(),
              let destDir = Logger.reportCollectionDirectory(),
              let data = FileManager.default.contents(atPath: configPath.path)
        else {
            return .failure(.missingConfigFile)
        }

        guard let contents = String(data: data, encoding: .utf8),
              let config = IssueReporterConfig.from(contents)
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
    ///
    /// - parameter entry:         a URL
    /// - parameter fileExtension: expected file extension
    ///
    /// - returns: true if a given entry is a regular file and ends with `fileExtension`
    private static func isReportURL(_ entry: URL, fileExtension: String) -> Bool {
        if let properties = try? entry.resourceValues(forKeys: [.nameKey, .isRegularFileKey]),
           let name = properties.name,
           let isFile = properties.isRegularFile {
            return isFile && name.hasSuffix(".\(fileExtension)")
        }
        return false
    }

    /// Copy the first file matching the config to the destination directory
    ///
    /// - parameter config:  base directory and file extension to use
    /// - parameter destDir: copy destination
    ///
    /// - returns: initialization resolution indicating whether a file was copied or not
    private static func copyFiles(config: IssueReporterConfig, destDir: URL) -> IssueReporterInitState {
        let resourceNames: [URLResourceKey] = [.nameKey, .isRegularFileKey, .contentModificationDateKey]
        guard let sourceDir = getCacheDir()?.appendingPathComponent(config.rootDir),
              let files = try? FileManager.default.contentsOfDirectory(at: sourceDir, includingPropertiesForKeys: resourceNames),
              let entry = files.sorted(by: { entry1, entry2 in
                if let props1 = try? entry1.resourceValues(forKeys: [.contentModificationDateKey]),
                   let props2 = try? entry2.resourceValues(forKeys: [.contentModificationDateKey]),
                   let modDate1 = props1.contentModificationDate,
                   let modDate2 = props2.contentModificationDate {
                    return modDate1 > modDate2 // newest files first
                }
                return false
              }).first(where: { entry in
                return isReportURL(entry, fileExtension: config.fileExtension)
              })
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
    ///
    /// - parameter entry: a file URL
    ///
    /// - returns: a new file name for `entry`, prepending the modification date as a Unix timestamp
    private static func getUploadFilename(_ entry: URL) -> String {
        if let properties = try? entry.resourceValues(forKeys: [.contentModificationDateKey]),
           let modDate = properties.contentModificationDate {
            let modDateMillis = UInt64(modDate.timeIntervalSince1970 * 1000)
            return "\(modDateMillis)_\(entry.lastPathComponent)"
        }
        return entry.lastPathComponent
    }
}

struct IssueReporterConfig {
    let rootDir: String
    let fileExtension: String

    static func from(_ text: String) -> IssueReporterConfig? {
        let components = text.split(separator: ",", maxSplits: 1)
        guard components.count == 2
        else {
            return nil
        }
        return IssueReporterConfig(
            rootDir: String(components[0]),
            fileExtension: String(components[1]))
    }
}

extension IssueReporterInitState: CustomStringConvertible {
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
            case .processingFailure(let error):
                return "CRASH_PROCESSING_FAILURE: \(error)"
            }
        }
    }
}
