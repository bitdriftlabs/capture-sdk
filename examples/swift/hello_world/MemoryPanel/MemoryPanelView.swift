// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

// MARK: - Section card shown in ContentView

struct MemoryPanelView: View {
    let loggerCustomer: LoggerCustomer
    @StateObject private var viewModel = MemoryLabViewModel()

    var body: some View {
        PanelSection(
            title: "Memory",
            subtitle: "Monitor memory usage and simulate low-memory pressure events."
        ) {
            NavigationLink(
                destination: MemoryLabView(loggerCustomer: self.loggerCustomer, viewModel: self.viewModel)
            ) {
                PanelRow(
                    title: "Memory lab",
                    subtitle: "\(self.viewModel.usedMB) MB / \(self.viewModel.limitMB) MB · \(self.viewModel.usagePercentFormatted) used",
                    showsChevron: true
                )
            }
            .buttonStyle(PressableCardButtonStyle())
        }
        .onAppear { self.viewModel.startMonitoring() }
        .onDisappear { self.viewModel.stopMonitoring() }
    }
}

// MARK: - Full lab screen

struct MemoryLabView: View {
    let loggerCustomer: LoggerCustomer
    @ObservedObject var viewModel: MemoryLabViewModel

    var body: some View {
        PanelScreen {
            self.gaugeSection
            self.simulatePressureSection
            self.allocateSection
        }
        .navigationTitle("Memory Lab")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { self.viewModel.startMonitoring() }
        .onDisappear { self.viewModel.stopMonitoring() }
    }

    // MARK: - Gauge

    private var gaugeSection: some View {
        PanelSection(
            title: "Current State",
            subtitle: "Device memory usage sampled every second."
        ) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("\(self.viewModel.usedMB) MB used")
                        .font(.headline)
                        .foregroundColor(Theme.textPrimary)
                    Spacer()
                    Text(self.viewModel.usagePercentFormatted)
                        .font(.headline.weight(.semibold))
                        .foregroundColor(self.viewModel.gaugeColor)
                }

                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Theme.surface)
                            .frame(height: 12)
                        RoundedRectangle(cornerRadius: 6)
                            .fill(self.viewModel.gaugeColor)
                            .frame(
                                width: geometry.size.width * CGFloat(self.viewModel.usagePercent / 100),
                                height: 12
                            )
                            .animation(.easeInOut(duration: 0.5), value: self.viewModel.usagePercent)
                    }
                }
                .frame(height: 12)

                Text("Limit: \(self.viewModel.limitMB) MB")
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary)
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 20).fill(Theme.surface))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Theme.border, lineWidth: 1))
        }
    }

    // MARK: - Simulate Pressure

    private var simulatePressureSection: some View {
        PanelSection(
            title: "Simulate Memory Pressure",
            subtitle: "Force the DispatchSource memory pressure handler to fire with a synthetic event."
        ) {
            VStack(spacing: 8) {
                Button(action: { self.loggerCustomer.simulateLowMemoryWarning(level: "warning") }) {
                    PanelRow(
                        title: "Trigger Warning",
                        subtitle: "Records a warning-level low memory event in the state store."
                    )
                }
                .buttonStyle(PressableCardButtonStyle())

                Button(action: { self.loggerCustomer.simulateLowMemoryWarning(level: "critical") }) {
                    PanelRow(
                        title: "Trigger Critical",
                        subtitle: "Records a critical-level low memory event in the state store.",
                        )
                }
                .buttonStyle(PressableCardButtonStyle())

                Button(action: { self.loggerCustomer.simulateLowMemoryWarning(level: "normal") }) {
                    PanelRow(
                        title: "Reset to Normal",
                        subtitle: "Clears the stored low memory state (normal level is filtered out of crash reports)."
                    )
                }
                .buttonStyle(PressableCardButtonStyle())
            }
        }
    }

    // MARK: - Force Memory Allocation

    private var allocateSection: some View {
        PanelSection(
            title: "Force Memory Allocation",
            subtitle: "Allocate physical memory to approach the OS memory limit and trigger real pressure events."
        ) {
            #if targetEnvironment(simulator)
            PanelRow(
                title: "Not available in Simulator",
                subtitle: "Allocating memory in the simulator does not trigger DispatchSource memory pressure events or Jetsam warnings. Run on a real device to test this.",
                badge: "⚠️",
                badgeColor: Theme.warning
            )
            #else
            VStack(spacing: 8) {
                ForEach([50, 70, 85, 90], id: \.self) { percent in
                    Button(action: { self.loggerCustomer.forceMemoryPressure(targetPercent: percent) }) {
                        PanelRow(
                            title: "Allocate to \(percent)%",
                            subtitle: "Fill memory until approximately \(percent)% of device RAM is used."
                        )
                    }
                    .buttonStyle(PressableCardButtonStyle())
                }

                Button(action: { self.loggerCustomer.clearMemoryPressure() }) {
                    PanelRow(
                        title: "Clear Allocations",
                        subtitle: "Release all forced allocations and return to baseline.",
                        badge: "Free",
                        badgeColor: Theme.primary
                    )
                }
                .buttonStyle(PressableCardButtonStyle())
            }
            #endif
        }
    }
}
