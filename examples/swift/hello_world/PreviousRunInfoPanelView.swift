// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import SwiftUI

struct PreviousRunInfoPanelView: View {
    @State private var previousRunInfo = Capture.Logger.previousRunInfo

    var body: some View {
        PanelSection(title: "Previous Run") {
            PanelCard(background: Color.black.opacity(0.24)) {
                VStack(alignment: .leading, spacing: 8) {
                    if let previousRunInfo {
                        PanelValueRow(title: "Status", value: statusLabel(previousRunInfo.status))
                        PanelValueRow(
                            title: "Was clean exit",
                            value: toggleString(previousRunInfo.wasCleanExit))
                        PanelValueRow(
                            title: "Legacy fatal crash",
                            value: toggleString(previousRunInfo.hasFatallyTerminated)
                        )
                    } else {
                        Text("Previous run info unavailable")
                            .font(.subheadline)
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
        }
        .onAppear(perform: refresh)
    }

    private func refresh() {
        self.previousRunInfo = Capture.Logger.previousRunInfo
    }

    private func toggleString(_ value: Bool) -> String {
        return value ? "Yes" : "No"
    }

    private func statusLabel(_ status: PreviousRunStatus) -> String {
        switch status {
        case .cleanExit:
            return "Clean Exit"
        case .fatalCrash:
            return "Fatal Crash"
        case .appUpdate:
            return "App Update"
        case .osUpdate:
            return "OS Update"
        case .unknown:
            return "Unknown"
        default:
            return "Unknown"
        }
    }
}

private struct PanelValueRow: View {
    let title: String
    let value: String

    var body: some View {
        HStack {
            Text("\(title):")
                .font(.caption)
                .foregroundColor(Theme.textSecondary)
            Spacer(minLength: 12)
            Text(value)
                .font(.system(.caption, design: .monospaced).weight(.semibold))
                .foregroundColor(Theme.textPrimary)
        }
    }
}
