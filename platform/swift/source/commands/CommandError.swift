// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

public struct CommandError: Error {
    public let title: String
    public let description: String?
    public let context: [String: String]

    public init(title: String, description: String? = nil, context: [String: String] = [:]) {
        self.title = title
        self.description = description
        self.context = context
    }
}
