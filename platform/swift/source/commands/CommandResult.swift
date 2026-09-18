// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

public struct CommandResult {
    public let attachment: CommandAttachment?
    public let context: [String: String]

    public init(attachment: CommandAttachment? = nil, context: [String: String] = [:]) {
        self.attachment = attachment
        self.context = context
    }
}
