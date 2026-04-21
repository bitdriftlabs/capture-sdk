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

    @State private var currentSessionID: String
    @State private var createdDeviceCode = "No code generated yet"
    @State private var selectedLogLevel = LoggerCustomer.LogLevel.info
    private let diagnosticColumns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]

    init() {
        Theme.applyNavigationAppearance()
        self.loggerCustomer = LoggerCustomer()
        self.currentSessionID = self.loggerCustomer.sessionID ?? "No Session ID"
    }

    var body: some View {
        NavigationView {
            PanelScreen {
                sessionPanel
                manualCapturePanel
                automaticCapturePanel
                diagnosticsPanel
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
                            .background(Theme.secondary.opacity(0.16))
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

    private var sessionPanel: some View {
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
                            background: Theme.secondary.opacity(0.12)
                        )
                    )

                    Text(self.createdDeviceCode)
                        .font(.system(.footnote, design: .monospaced))
                        .foregroundColor(
                            self.createdDeviceCode == "No code generated yet"
                                ? Theme.textSecondary
                                : Theme.textPrimary
                        )
                        .textSelection(.enabled)
                }
            }
        }
    }

    private var manualCapturePanel: some View {
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
                    badge: self.selectedLogLevel.badgeLabel,
                    badgeColor: self.selectedLogLevel.tint,
                    showsChevron: true
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            Button(action: { self.loggerCustomer.simulateSpan() }) {
                PanelRow(
                    title: "Span event",
                    subtitle: "Creates a sample span and closes it after a short delay.",
                    badge: "event"
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            Button(action: { self.loggerCustomer.simulateNavigation() }) {
                PanelRow(
                    title: "Screen navigation",
                    subtitle: "Logs multiple screen views to exercise navigation tracking.",
                    badge: "nav"
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            Button(action: {
                self.loggerCustomer.setFeatureFlagExposure(name: "MyFlag", variant: "MyVariant")
            }) {
                PanelRow(
                    title: "Feature flag exposure",
                    subtitle: "Sends the demo flag `MyFlag` with variant `MyVariant`.",
                    badge: "flag",
                    badgeColor: Theme.primary
                )
            }
            .buttonStyle(PressableCardButtonStyle())
        }
    }

    var automaticCapturePanel: some View {
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
                    badge: self.selectedLogLevel.badgeLabel,
                    badgeColor: self.selectedLogLevel.tint,
                    showsChevron: true
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            Button(action: { self.loggerCustomer.simulateSpan() }) {
                PanelRow(
                    title: "Span event",
                    subtitle: "Creates a sample span and closes it after a short delay.",
                    badge: "event"
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            Button(action: { self.loggerCustomer.simulateNavigation() }) {
                PanelRow(
                    title: "Screen navigation",
                    subtitle: "Logs multiple screen views to exercise navigation tracking.",
                    badge: "nav"
                )
            }
            .buttonStyle(PressableCardButtonStyle())

            Button(action: {
                self.loggerCustomer.setFeatureFlagExposure(name: "MyFlag", variant: "MyVariant")
            }) {
                PanelRow(
                    title: "Feature flag exposure",
                    subtitle: "Sends the demo flag `MyFlag` with variant `MyVariant`.",
                    badge: "flag",
                    badgeColor: Theme.primary
                )
            }
            .buttonStyle(PressableCardButtonStyle())
        }
    }

    var diagnosticsPanel: some View {
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

    private func generateTemporaryDeviceCode() {
        self.createdDeviceCode = "Generating device code..."
        self.loggerCustomer.createTemporaryDeviceCode(completion: { result in
            switch result {
            case let .success(code):
                self.createdDeviceCode = code
                UIPasteboard.general.string = code
            case let .failure(error):
                self.createdDeviceCode = String(describing: error)
            }
        })
    }
}
