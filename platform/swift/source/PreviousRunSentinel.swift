// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

final class PreviousRunSentinel {
    private enum State: String {
        case foreground
        case background
    }

    private let fileURL: URL
    private let fileManager: FileManager

    init(fileURL: URL, fileManager: FileManager = .default) {
        self.fileURL = fileURL
        self.fileManager = fileManager
    }

    func previousRunEndedUnexpectedly() -> Bool {
        guard let state = self.readState() else {
            return false
        }
        return state == .foreground
    }

    func markForeground() {
        self.writeState(.foreground)
    }

    func markBackground() {
        self.writeState(.background)
    }

    func clear() {
        try? self.fileManager.removeItem(at: self.fileURL)
    }

    private func readState() -> State? {
        guard let data = try? Data(contentsOf: self.fileURL),
              let rawValue = String(data: data, encoding: .utf8),
              let state = State(rawValue: rawValue)
        else {
            return nil
        }
        return state
    }

    private func writeState(_ state: State) {
        let directory = self.fileURL.deletingLastPathComponent()
        try? self.fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        try? Data(state.rawValue.utf8).write(to: self.fileURL, options: .atomic)
    }
}
