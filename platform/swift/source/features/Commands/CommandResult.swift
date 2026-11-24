// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) public struct CommandResult: Equatable, Sendable {
    public let attachment: CommandAttachment?
    /// Fields to report with the successful command. Command outcome logs reserve `_result`,
    /// `_duration_ms`, and keys starting with `_command_`, `_workflow_command_`, or `_span_`.
    public let context: [String: String]

    public init(
        attachment: CommandAttachment? = nil,
        context: [String: String] = [:]
    ) {
        self.attachment = attachment
        self.context = context
    }
}
