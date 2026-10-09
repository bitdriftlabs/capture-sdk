// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

public enum CommandError: Error, Equatable, Sendable {
    case notFound
    case alreadyExecuting
    case maximumConcurrency
    case invalidArguments(description: String? = nil)
    case handlerFailed(title: String, description: String?, context: [String: String])

    var code: String {
        switch self {
        case .notFound:
            "command_unknown"
        case .alreadyExecuting:
            "command_already_executing"
        case .maximumConcurrency:
            "max_command_concurrency"
        case .invalidArguments:
            "invalid_arguments"
        case .handlerFailed:
            "handler_failed"
        }
    }

    public init(
        title: String,
        description: String? = nil,
        context: [String: String] = [:]
    ) {
        self = .handlerFailed(title: title, description: description, context: context)
    }

    public var title: String {
        switch self {
        case .notFound:
            "Command not found"
        case .alreadyExecuting:
            "Command is already executing"
        case .maximumConcurrency:
            "Maximum command concurrency reached"
        case .invalidArguments:
            "Invalid command arguments"
        case let .handlerFailed(title, _, _):
            title
        }
    }

    public var description: String? {
        switch self {
        case let .invalidArguments(description), let .handlerFailed(_, description, _):
            description
        default:
            nil
        }
    }

    public var context: [String: String] {
        switch self {
        case let .handlerFailed(_, _, context):
            context
        default:
            [:]
        }
    }
}
