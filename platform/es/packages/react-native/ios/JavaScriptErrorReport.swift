// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// FFI function declaration for persisting JavaScript error reports to disk
@_silgen_name("capture_persist_javascript_error_report")
func capture_persist_javascript_error_report(
    _ error_name: UnsafePointer<CChar>,
    _ error_message: UnsafePointer<CChar>,
    _ stack_trace: UnsafePointer<CChar>,
    _ is_fatal: Bool,
    _ engine: UnsafePointer<CChar>,
    _ debug_id: UnsafePointer<CChar>?,
    _ timestamp_seconds: UInt64,
    _ timestamp_nanos: UInt32,
    _ destination_path: UnsafePointer<CChar>,
    _ manufacturer: UnsafePointer<CChar>,
    _ model: UnsafePointer<CChar>,
    _ os_version: UnsafePointer<CChar>,
    _ os_brand: UnsafePointer<CChar>,
    _ app_id: UnsafePointer<CChar>,
    _ app_version: UnsafePointer<CChar>,
    _ version_code: UnsafePointer<CChar>,
    _ sdk_version: UnsafePointer<CChar>
)

enum JavaScriptErrorReport {
    /// Persists a JavaScript error report to disk
    static func persist(
        errorName: String,
        message: String,
        stack: String,
        isFatal: Bool,
        engine: String,
        debugId: String,
        timestampSeconds: UInt64,
        timestampNanos: UInt32,
        destinationPath: String,
        deviceMetadata: DeviceMetadata,
        appMetadata: AppMetadata,
        sdkVersion: String
    ) {
        let strings = [
            errorName,
            message,
            stack,
            engine,
            destinationPath,
            deviceMetadata.manufacturer,
            deviceMetadata.model,
            deviceMetadata.osVersion,
            deviceMetadata.osBrand,
            appMetadata.id,
            appMetadata.version,
            appMetadata.versionCode,
            sdkVersion,
            debugId
        ]
        
        strings.withCStrings { cStrings in
            capture_persist_javascript_error_report(
                cStrings[0],  // errorName
                cStrings[1],  // message
                cStrings[2],  // stack
                isFatal,
                cStrings[3],  // engine
                cStrings[13], // debugId
                timestampSeconds,
                timestampNanos,
                cStrings[4],  // destinationPath
                cStrings[5],  // manufacturer
                cStrings[6],  // model
                cStrings[7],  // osVersion
                cStrings[8],  // osBrand
                cStrings[9],  // appId
                cStrings[10], // appVersion
                cStrings[11], // versionCode
                cStrings[12]  // sdkVersion
            )
        }
    }
}

/// Helper extension for converting Swift strings to C strings for FFI calls
extension Array where Element == String {
    func withCStrings<Result>(_ body: ([UnsafePointer<CChar>]) -> Result) -> Result {
        var cStrings: [UnsafePointer<CChar>] = []
        
        func build(_ index: Int) -> Result {
            if index == count {
                return body(cStrings)
            }
            return self[index].withCString { ptr in
                cStrings.append(ptr)
                defer { cStrings.removeLast() }
                return build(index + 1)
            }
        }
        
        return build(0)
    }
}

