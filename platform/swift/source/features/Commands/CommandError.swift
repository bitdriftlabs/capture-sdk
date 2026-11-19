// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) public struct CommandError: Error, Equatable, Sendable {
    private enum Kind: Equatable, Sendable {
        case commandUnknown
        case commandAlreadyExecuting
        case maxCommandConcurrency
        case invalidArguments(message: String)
        case handlerFailed(message: String, context: [String: String])
    }

    private let kind: Kind

    /// Creates an error indicating that a command handler could not complete its work.
    ///
    /// - parameter message: The human-readable failure message.
    ///
    /// - parameter context: Fields to report with the failed command, separate from the failure message.
    ///                      Command outcome logs reserve `_result`, `_duration_ms`, and keys
    ///                      starting with `_command_`, `_workflow_command_`, or `_span_`.
    public init(
        message: String,
        context: [String: String] = [:]
    ) {
        kind = .handlerFailed(message: message, context: context)
    }

    /// Creates an error indicating that the command's arguments could not be used.
    ///
    /// - parameter message: Details about why the arguments were invalid.
    ///
    /// - returns: An invalid-arguments error.
    public static func invalidArguments(message: String = "Invalid command arguments") -> CommandError {
        CommandError(kind: .invalidArguments(message: message))
    }

    static let notFound = CommandError(kind: .commandUnknown)
    static let alreadyExecuting = CommandError(kind: .commandAlreadyExecuting)
    static let maximumConcurrency = CommandError(kind: .maxCommandConcurrency)
    static let invalidAttachment = CommandError(message: "attachment is not a local file")

    private init(kind: Kind) {
        self.kind = kind
    }

    var code: String {
        switch kind {
        case .commandUnknown:
            "command_unknown"
        case .commandAlreadyExecuting:
            "command_already_executing"
        case .maxCommandConcurrency:
            "max_command_concurrency"
        case .invalidArguments:
            "invalid_arguments"
        case .handlerFailed:
            "handler_failed"
        }
    }

    public var message: String {
        switch kind {
        case .commandUnknown:
            "Command not found"
        case .commandAlreadyExecuting:
            "Command is already executing"
        case .maxCommandConcurrency:
            "Maximum command concurrency reached"
        case let .invalidArguments(message), let .handlerFailed(message, _):
            message
        }
    }

    public var context: [String: String] {
        switch kind {
        case let .handlerFailed(_, context):
            context
        default:
            [:]
        }
    }
}
