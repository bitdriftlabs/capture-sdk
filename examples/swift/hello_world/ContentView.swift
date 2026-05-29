// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct ContentView: View {
    private let loggerCustomer: LoggerCustomer
    @StateObject private var crashPanelViewModel: CrashPanelViewModel

    init(
        loggerCustomer: LoggerCustomer,
        crashPanelViewModel: CrashPanelViewModel
    ) {
        self.loggerCustomer = loggerCustomer
        _crashPanelViewModel = StateObject(wrappedValue: crashPanelViewModel)
    }

    var body: some View {
        NavigationView {
            PanelScreen {
                SdkStatusPanelView()
                SessionPanelView(loggerCustomer: self.loggerCustomer)
                ManualCapturePanelView(loggerCustomer: self.loggerCustomer)
                AutomaticCapturePanelView(loggerCustomer: self.loggerCustomer)
                CrashPanelView(viewModel: self.crashPanelViewModel)
                MemoryPanelView(loggerCustomer: self.loggerCustomer)
                DiagnosticsPanelView(loggerCustomer: self.loggerCustomer)
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
                }.removeToolBarItemGlassStyle()
            }
        }
        .navigationViewStyle(.stack)
        .accentColor(Theme.primary)
        .onAppear { self.loggerCustomer.logAppLaunchTTI() }
    }
}

extension ToolbarContent {
    func removeToolBarItemGlassStyle() -> some ToolbarContent {
        if #available(iOS 26.0, *) {
            return self.sharedBackgroundVisibility(.hidden)
        } else {
            return self
        }
    }
}
