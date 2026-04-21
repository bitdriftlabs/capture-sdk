// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct AutomaticCapturePanelView: View {
    var loggerCustomer: LoggerCustomer

    var body: some View {
        PanelSection(
            title: "Automatic capture",
            subtitle: "Triggers that become SDK telemetry through existing automatic integrations."
        ) {
            Button(action: { self.loggerCustomer.performRandomNetworkRequestUsingDataTask() }) {
                PanelRow(
                    title: "Random network request",
                    subtitle: "Exercises URLSession auto-capture with one of the bundled sample requests.",
                    badge: "auto",
                    badgeColor: Theme.primary
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            PanelCard {
                Text("App launch TTI is logged automatically on startup. Relaunch the app if you want to generate that signal again.")
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary)
            }
        }
    }
}
