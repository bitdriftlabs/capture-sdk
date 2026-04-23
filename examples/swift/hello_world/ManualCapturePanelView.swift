// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct ManualCapturePanelView: View {
    @State private var selectedLogLevel = LoggerCustomer.LogLevel.info
    var loggerCustomer: LoggerCustomer

    var body: some View {
        PanelSection(
            title: "Manual capture",
            subtitle: "Explicit events you trigger from the debug panel."
        ) {
            NavigationLink(
                destination: LogComposerView(
                    loggerCustomer: self.loggerCustomer,
                    selectedLogLevel: self.$selectedLogLevel
                )
            ) {
                PanelRow(
                    title: "Manual log",
                    subtitle: "Choose a level and compose a log event before sending it.",
                    showsChevron: true
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            Button(action: { self.loggerCustomer.simulateSpan() }) {
                PanelRow(
                    title: "Span event",
                    subtitle: "Creates a sample span and closes it after a short delay."
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            Button(action: { self.loggerCustomer.simulateNavigation() }) {
                PanelRow(
                    title: "Screen navigation",
                    subtitle: "Logs multiple screen views to exercise navigation tracking."
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            Button(action: {
                self.loggerCustomer.setFeatureFlagExposure(name: "MyFlag", variant: "MyVariant")
            }) {
                PanelRow(
                    title: "Feature flag exposure",
                    subtitle: "Sends the demo flag `MyFlag` with variant `MyVariant`."
                )
            }
            .buttonStyle(PressableCardButtonStyle())
        }
    }
}
