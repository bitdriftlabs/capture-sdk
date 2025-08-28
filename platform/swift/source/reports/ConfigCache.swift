// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// Parse text for comma-delimited key/value pairs
///
/// - parameter text: text to parse
///
/// - returns: A mapping between keys and values, upon success
package func readCachedValues(_ text: String) -> [String: Any]? {
    var values: [String: Any] = [:]
    for line in text.split(separator: "\n") {
        let pair = line.split(separator: ",", maxSplits: 1)
        if pair.count == 2 {
            let key = String(pair[0])
            switch pair[1] {
            case "true":
                values[key] = true
            case "false":
                values[key] = false
            default:
                values[key] = String(pair[1])
            }
        } else {
            // failed to fully parse file, contains errors
            return nil
        }
    }
    return values
}
