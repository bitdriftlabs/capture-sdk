// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import SwiftUI

struct SdkStatusPanelView: View {
    @State private var sdkStatus: SdkStatus?
    @State private var isTracingActive = false
    @State private var lastCheckTime: Date?

    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        return formatter
    }()

    var body: some View {
        PanelSection(title: "SDK State") {
            PanelCard(background: Color.black.opacity(0.24)) {
                if let status = sdkStatus {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("State:")
                                .font(.subheadline.weight(.medium))
                                .foregroundColor(Theme.textSecondary)
                            Text(stateLabel(for: status.initializationState))
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(
                                    status.initializationState == .running
                                        ? Theme.primary
                                        : Theme.textSecondary
                                )
                        }

                        HStack {
                            Text("Tracing active:")
                                .font(.caption)
                                .foregroundColor(Theme.textSecondary)
                            Text(isTracingActive ? "Yes" : "No")
                                .font(.system(.caption, design: .monospaced))
                                .foregroundColor(isTracingActive ? Theme.primary : Theme.textPrimary)
                        }

                        if let handshake = status.lastHandshakeTime {
                            HStack {
                                Text("Last handshake:")
                                    .font(.caption)
                                    .foregroundColor(Theme.textSecondary)
                                Text(dateFormatter.string(from: handshake))
                                    .font(.system(.caption, design: .monospaced))
                                    .foregroundColor(Theme.textPrimary)
                            }
                        } else {
                            Text("No handshake yet")
                                .font(.caption)
                                .foregroundColor(Theme.textSecondary)
                        }

                        if let configDelivery = status.lastConfigDeliveryTime {
                            HStack {
                                Text("Last config delivery:")
                                    .font(.caption)
                                    .foregroundColor(Theme.textSecondary)
                                Text(dateFormatter.string(from: configDelivery))
                                    .font(.system(.caption, design: .monospaced))
                                    .foregroundColor(Theme.textPrimary)
                            }
                        } else {
                            Text("No config delivery yet")
                                .font(.caption)
                                .foregroundColor(Theme.textSecondary)
                        }

                        if let checkTime = lastCheckTime {
                            Text("Checked at: \(dateFormatter.string(from: checkTime))")
                                .font(.caption2)
                                .foregroundColor(Theme.textSecondary.opacity(0.7))
                        }
                    }
                } else {
                    Text("Not checked yet")
                        .font(.subheadline)
                        .foregroundColor(Theme.textSecondary)
                }

                Button(action: checkSdkStatus) {
                    Text("Check SDK State")
                }
                .buttonStyle(
                    OutlineButtonStyle(
                        stroke: Theme.secondary,
                        foreground: Theme.textPrimary,
                        background: Theme.secondary.opacity(0.10)
                    )
                )
            }
        }
        .onAppear {
            checkSdkStatus()
        }
    }

    private func checkSdkStatus() {
        sdkStatus = Capture.Logger.getSdkStatus()
        isTracingActive = Capture.Logger.isTracingActive
        lastCheckTime = Date()
    }

    private func stateLabel(for state: InitializationState) -> String {
        switch state {
        case .notStarted:
            return "Not Started"
        case .loaded:
            return "Loaded"
        case .running:
            return "Running"
        case .disabled:
            return "Disabled"
        @unknown default:
            return "Unknown"
        }
    }
}
