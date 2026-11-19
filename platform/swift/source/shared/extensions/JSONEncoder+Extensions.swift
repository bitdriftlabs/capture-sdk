// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension JSONEncoder {
    // The default Encoder to use for encoding log fields.
    public static func makeDefault() -> JSONEncoder {
        let encoder = JSONEncoder()
        // Sorting below should help with parsing the encoding output but may lead to
        // negative performance impact.
        let formattingOptions: JSONEncoder.OutputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        encoder.outputFormatting = formattingOptions
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            let formatted = ISO8601DateFormatter.default.string(from: date)
            try container.encode(formatted)
        }

        return encoder
    }
}
