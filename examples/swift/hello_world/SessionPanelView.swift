// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct SessionPanelView: View {
    @State private var deviceCode = "No code generated yet"
    @State var currentSessionID: String = ""
    var loggerCustomer: LoggerCustomer

    init(loggerCustomer: LoggerCustomer) {
        self.loggerCustomer = loggerCustomer
    }

    var body: some View {
        PanelSection(title: "Session") {
            PanelCard(background: Color.black.opacity(0.24)) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Current session ID")
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(Theme.textSecondary)

                    Text(self.currentSessionID)
                        .font(.system(.body, design: .monospaced).weight(.semibold))
                        .foregroundColor(Theme.textPrimary)
                        .textSelection(.enabled)
                        .lineLimit(1)
                        .minimumScaleFactor(0.3)
                }

                HStack(spacing: 12) {
                    Button(action: {
                        self.loggerCustomer.startNewSession()
                        self.currentSessionID = self.loggerCustomer.sessionID ?? "No Session ID"
                    }) {
                        Text("New session")
                    }
                    .buttonStyle(
                        FilledButtonStyle(
                            fill: Theme.primary,
                            foreground: Theme.textPrimary
                        )
                    )

                    Button(action: {
                        UIPasteboard.general.string = self.loggerCustomer.sessionURL
                    }) {
                        Text("Copy URL")
                    }
                    .buttonStyle(
                        OutlineButtonStyle(
                            stroke: Theme.textSecondary,
                            foreground: Theme.textPrimary,
                            background: Color.white.opacity(0.05)
                        )
                    )
                    .disabled(self.loggerCustomer.sessionURL == nil)
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("Temporary device code")
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(Theme.textSecondary)

                    Button(action: self.generateTemporaryDeviceCode) {
                        Text("Generate and copy")
                    }
                    .buttonStyle(
                        OutlineButtonStyle(
                            stroke: Theme.secondary,
                            foreground: Theme.textPrimary,
                            background: Theme.secondary.opacity(0.10)
                        )
                    )

                    Text(self.deviceCode)
                        .font(.system(.footnote, design: .monospaced))
                        .foregroundColor(
                            self.deviceCode == "No code generated yet"
                                ? Theme.textSecondary
                                : Theme.textPrimary
                        )
                        .textSelection(.enabled)
                }
            }
        }
        .onAppear {
            self.currentSessionID = loggerCustomer.sessionID ?? "No Session ID"
        }
    }

    private func generateTemporaryDeviceCode() {
        self.deviceCode = "Generating device code..."
        self.loggerCustomer.createTemporaryDeviceCode(completion: { result in
            switch result {
            case let .success(code):
                self.deviceCode = code
                UIPasteboard.general.string = code
            case let .failure(error):
                self.deviceCode = String(describing: error)
            }
        })
    }
}
