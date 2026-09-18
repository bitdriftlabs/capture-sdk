// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Objective-C representation of a command completion.
@objc(CAPCommandResult)
public final class CommandResultObjc: NSObject {
    @objc public let isSuccess: Bool
    @objc public let attachment: CommandAttachment?
    @objc public let context: [String: String]
    @objc public let title: String?
    @objc public let failureDescription: String?

    private let result: Result<CommandResult, CommandError>

    private init(result: Result<CommandResult, CommandError>) {
        self.result = result

        switch result {
        case .success(let commandResult):
            self.isSuccess = true
            self.attachment = commandResult.attachment
            self.context = commandResult.context
            self.title = nil
            self.failureDescription = nil
        case .failure(let commandError):
            self.isSuccess = false
            self.attachment = nil
            self.context = commandError.context
            self.title = commandError.title
            self.failureDescription = commandError.description
        }
    }

    @objc(successWithAttachment:context:)
    public static func success(
        attachment: CommandAttachment? = nil,
        context: [String: String] = [:]
    ) -> CommandResultObjc {
        CommandResultObjc(result: .success(CommandResult(attachment: attachment, context: context)))
    }

    @objc(failureWithTitle:description:context:)
    public static func failure(
        title: String,
        description: String? = nil,
        context: [String: String] = [:]
    ) -> CommandResultObjc {
        CommandResultObjc(result: .failure(CommandError(
            title: title,
            description: description,
            context: context
        )))
    }

    var swiftResult: Result<CommandResult, CommandError> {
        self.result
    }
}
