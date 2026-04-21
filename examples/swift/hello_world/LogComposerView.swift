// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct LogComposerView: View {
    let loggerCustomer: LoggerCustomer
    @Binding var selectedLogLevel: LoggerCustomer.LogLevel

    @State private var message = "Manual log from the debug panel"

    var body: some View {
        PanelScreen {
            PanelSection(
                title: "Manual logging",
                subtitle: "Pick a level from the SDK enum and send a structured demo event."
            ) {
                PanelCard {
                    PanelInputField(
                        title: "Message",
                        placeholder: "Enter log message",
                        text: self.$message
                    )

                    VStack(spacing: 12) {
                        ForEach(LoggerCustomer.LogLevel.allCases) { level in
                            Button(action: {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    self.selectedLogLevel = level
                                }
                            }) {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(level.displayName)
                                            .font(.headline)
                                            .foregroundColor(Theme.textPrimary)

                                        Text(level.description)
                                            .font(.subheadline)
                                            .foregroundColor(Theme.textSecondary)
                                    }

                                    Spacer()

                                    Image(systemName: self.selectedLogLevel == level
                                        ? "checkmark.circle.fill"
                                        : "circle"
                                    )
                                    .font(.title3)
                                    .foregroundColor(level.tint)
                                    .animation(.easeInOut(duration: 0.2), value: self.selectedLogLevel)
                                }
                                .padding(16)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(
                                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                                        .fill(Theme.surface)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                                        .stroke(
                                            self.selectedLogLevel == level ? level.tint : Theme.border,
                                            lineWidth: 1
                                        )
                                        .animation(.easeInOut(duration: 0.2), value: self.selectedLogLevel)
                                )
                            }
                            .buttonStyle(PressableCardButtonStyle(cornerRadius: 18))
                        }
                    }

                    Button(action: {
                        self.loggerCustomer.log(
                            with: self.selectedLogLevel,
                            message: self.message
                        )
                    }) {
                        Text("Send \(self.selectedLogLevel.displayName) log")
                    }
                    .buttonStyle(
                        FilledButtonStyle(
                            fill: self.selectedLogLevel.tint,
                            foreground: Theme.textPrimary
                        )
                    )
                }
            }

            PanelCard {
                Text("Every sample log keeps the existing demo fields from `LoggerCustomer` and only swaps the message/level.")
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary)
            }
        }
        .navigationTitle("Logging")
    }
}

extension LoggerCustomer.LogLevel {
    var displayName: String {
        self.rawValue.capitalized
    }

    var badgeLabel: String {
        self.rawValue
    }

    var description: String {
        switch self {
        case .error:
            return "Use for failures that should stand out immediately."
        case .warning:
            return "Signals suspicious behavior without breaking the flow."
        case .info:
            return "Useful for normal milestones and user-driven actions."
        case .debug:
            return "Great for noisy state changes while iterating locally."
        case .trace:
            return "Most verbose level for low-level execution details."
        }
    }

    var tint: Color {
        switch self {
        case .error:
            return Theme.danger
        case .warning:
            return Theme.warning
        case .info:
            return Theme.primary
        case .debug:
            return Theme.secondary
        case .trace:
            return Theme.textSecondary
        }
    }
}
