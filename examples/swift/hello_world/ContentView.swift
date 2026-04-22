// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import SwiftUI

struct ContentView: View {
    private let loggerCustomer: LoggerCustomer

    init() {
        self.loggerCustomer = LoggerCustomer()
    }

    var body: some View {
        NavigationView {
            PanelScreen {
                SessionPanelView(loggerCustomer: loggerCustomer)
                ManualCapturePanelView(loggerCustomer: loggerCustomer)
                AutomaticCapturePanelView(loggerCustomer: loggerCustomer)
                DiagnosticsPanelView(loggerCustomer: loggerCustomer)
            }
            .navigationTitle("Debug Panel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    NavigationLink(destination: ConfigurationView()) {
                        Text("Config")
                            .font(.body.weight(.semibold))
                            .foregroundColor(Theme.textPrimary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Theme.secondary.opacity(0.1))
                            .overlay(
                                Capsule()
                                    .stroke(Theme.secondary.opacity(0.45), lineWidth: 1)
                            )
                            .clipShape(Capsule())
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
        .accentColor(Theme.primary)
        .onAppear { self.loggerCustomer.logAppLaunchTTI() }
    }
}
