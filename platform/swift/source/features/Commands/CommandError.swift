// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

public struct CommandError: Error, Equatable, Sendable {
    private enum Kind: Equatable, Sendable {
        case commandUnknown
        case commandAlreadyExecuting
        case maxCommandConcurrency
        case invalidArguments(description: String?)
        case handlerFailed(title: String, description: String?, context: [String: String])
    }

    private let kind: Kind

    /// Creates an error indicating that a command handler could not complete its work.
    ///
    /// - parameter title:       A short description of the failure.
    ///
    /// - parameter description: Additional details about the failure.
    ///
    /// - parameter context:     Fields to report with the failed command.
    public init(
        title: String,
        description: String? = nil,
        context: [String: String] = [:]
    ) {
        kind = .handlerFailed(title: title, description: description, context: context)
    }

    /// Creates an error indicating that the command's arguments could not be used.
    ///
    /// - parameter description: Details about why the arguments were invalid.
    ///
    /// - returns: An invalid-arguments error.
    public static func invalidArguments(description: String? = nil) -> CommandError {
        CommandError(kind: .invalidArguments(description: description))
    }

    static let notFound = CommandError(kind: .commandUnknown)
    static let alreadyExecuting = CommandError(kind: .commandAlreadyExecuting)
    static let maximumConcurrency = CommandError(kind: .maxCommandConcurrency)

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

    public var title: String {
        switch kind {
        case .commandUnknown:
            "Command not found"
        case .commandAlreadyExecuting:
            "Command is already executing"
        case .maxCommandConcurrency:
            "Maximum command concurrency reached"
        case .invalidArguments:
            "Invalid command arguments"
        case let .handlerFailed(title, _, _):
            title
        }
    }

    public var description: String? {
        switch kind {
        case let .invalidArguments(description), let .handlerFailed(_, description, _):
            description
        default:
            nil
        }
    }

    public var context: [String: String] {
        switch kind {
        case let .handlerFailed(_, _, context):
            context
        default:
            [:]
        }
    }
}
