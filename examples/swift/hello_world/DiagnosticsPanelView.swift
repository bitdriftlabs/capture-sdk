// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct DiagnosticsPanelView: View {
    var loggerCustomer: LoggerCustomer
    private let diagnosticColumns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]

    var body: some View {
        PanelSection(
            title: "Diagnostics / pressure",
            subtitle: "Stress the SDK with failures, ANR simulation, and memory pressure."
        ) {
            LazyVGrid(columns: self.diagnosticColumns, spacing: 12) {
                Button(action: {
                    let items = [1, 2, 3]
                    print("the fourth item: \(items[3])")
                }) {
                    Text("Assertion failure")
                }
                .buttonStyle(
                    OutlineButtonStyle(
                        stroke: Theme.danger.opacity(0.7),
                        foreground: Theme.danger,
                        background: Theme.danger.opacity(0.08)
                    )
                )

                Button(action: { Thread.sleep(forTimeInterval: 5.0) }) {
                    Text("ANR (5s)")
                }
                .buttonStyle(
                    OutlineButtonStyle(
                        stroke: Theme.warning.opacity(0.8),
                        foreground: Theme.warning,
                        background: Theme.warning.opacity(0.08)
                    )
                )

                Button(action: { self.loggerCustomer.forceMemoryPressure(targetPercent: 90) }) {
                    Text("Memory 90%")
                }
                .buttonStyle(
                    OutlineButtonStyle(
                        stroke: Theme.secondary.opacity(0.7),
                        foreground: Theme.textPrimary,
                        background: Theme.secondary.opacity(0.12)
                    )
                )

                Button(action: { self.loggerCustomer.clearMemoryPressure() }) {
                    Text("Clear pressure")
                }
                .buttonStyle(
                    OutlineButtonStyle(
                        stroke: Theme.textSecondary.opacity(0.7),
                        foreground: Theme.textPrimary,
                        background: Color.white.opacity(0.05)
                    )
                )
            }
        }
    }
}
