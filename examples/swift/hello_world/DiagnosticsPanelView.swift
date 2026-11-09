// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct DiagnosticsPanelView: View {
    var loggerCustomer: LoggerCustomer

    var body: some View {
        PanelSection(
            title: "Diagnostics / pressure",
            subtitle: "Stress the SDK with failures, ANR simulation, and memory pressure."
        ) {
            VStack {
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
            }
        }
    }
}
