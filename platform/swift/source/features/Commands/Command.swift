// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

public struct Command: Sendable {
    let key: String
    let handler: CommandHandler

    public init(key: String, handler: @escaping CommandHandler) {
        self.key = key
        self.handler = handler
    }
}

extension Array where Element == Command {
    /// Removes the duplicates, keeping the first one it finds in the array
    func removingDuplicates() -> (
        commands: [Command],
        duplicates: [Command]
    ) {
        var seenKeys = Set<String>()
        var commands: [Command] = []
        var duplicates: [Command] = []
        for command in self {
            if seenKeys.insert(command.key).inserted {
                commands.append(command)
            } else {
                duplicates.append(command)
            }
        }
        return (commands, duplicates)
    }
}
