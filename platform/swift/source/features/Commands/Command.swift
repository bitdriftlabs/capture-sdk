// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

@_spi(ExperimentalAPI) public struct Command: Sendable {
    let key: String
    let handler: CommandHandler

    public init(key: String, handler: @escaping CommandHandler) {
        self.key = key
        self.handler = handler
    }
}

extension Array where Element == Command {
    /// Removes duplicate commands, retaining the last command for each key.
    ///
    /// - returns: The unique commands and the commands removed as duplicates.
    func removingDuplicates() -> (
        commands: [Command],
        duplicates: [Command]
    ) {
        var seenKeys = Set<String>()
        var commands: [Command] = []
        var duplicates: [Command] = []
        for command in reversed() {
            if seenKeys.insert(command.key).inserted {
                commands.append(command)
            } else {
                duplicates.append(command)
            }
        }
        return (commands.reversed(), duplicates.reversed())
    }
}
