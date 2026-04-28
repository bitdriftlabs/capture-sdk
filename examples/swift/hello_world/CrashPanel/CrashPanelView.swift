// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct CrashPanelView: View {
    @ObservedObject var viewModel: CrashPanelViewModel

    var body: some View {
        PanelSection(
            title: "Crashes",
            subtitle: "Open the dedicated crash lab with environment guidance, crash triggers, and recent MetricKit diagnostics."
        ) {
            NavigationLink(destination: CrashesView(viewModel: self.viewModel)) {
                PanelRow(
                    title: "Crash lab",
                    subtitle: self.crashLabSubtitle,
                    badge: self.viewModel.recentCrashDiagnostics.isEmpty
                        ? nil
                        : "\(self.viewModel.recentCrashDiagnostics.count)",
                    badgeColor: Theme.primary,
                    showsChevron: true
                )
            }
            .buttonStyle(PressableCardButtonStyle())
        }
    }

    private var crashLabSubtitle: String {
        if self.viewModel.recentCrashDiagnostics.isEmpty {
            return "Review caveats before crashing the app and inspect MetricKit payloads after relaunch."
        }

        let count = self.viewModel.recentCrashDiagnostics.count
        return "Review caveats, trigger crashes, and inspect \(count) recent MetricKit diagnostic\(count == 1 ? "" : "s")."
    }
}

struct CrashesView: View {
    @ObservedObject var viewModel: CrashPanelViewModel

    private let registry = CrashRegistry.shared

    var body: some View {
        PanelScreen {
            self.environmentSection
            self.metricKitSection

            ForEach(CrashCategory.allCases, id: \.rawValue) { category in
                PanelSection(
                    title: category.rawValue,
                    subtitle: category.subtitle
                ) {
                    let crashes = self.registry.crashes(in: category)
                    ForEach(Array(crashes.enumerated()), id: \.offset) { item in
                        let crash = item.element
                        Button(action: { crash.trigger() }) {
                            PanelRow(
                                title: crash.title,
                                subtitle: crash.crashDescription
                            )
                        }
                        .buttonStyle(PressableCardButtonStyle())
                    }
                }
            }
        }
        .navigationTitle("Crashes")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var environmentSection: some View {
        let snapshot = self.viewModel.crashEnvironment
        let overallStatus = self.overallStatus(for: snapshot)

        return PanelSection(
            title: "Environment",
            subtitle: "Crash reporting changes a lot between simulator, debugger-attached runs, and release builds."
        ) {
            PanelCard(background: overallStatus.background) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            Text("Current status")
                                .font(.headline)
                                .foregroundColor(Theme.textPrimary)
                            PanelBadge(text: overallStatus.badge, color: overallStatus.color)
                        }

                        Text(overallStatus.message)
                            .font(.subheadline)
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }

                VStack(alignment: .leading, spacing: 12) {
                    CrashEnvironmentInfoRow(
                        title: "Hardware",
                        badge: snapshot.isSimulator ? "Simulator" : "Device",
                        badgeColor: snapshot.isSimulator ? Theme.warning : Theme.primary,
                        detail: snapshot.isSimulator
                            ? "Fatal issue reporting is unsupported on the iOS simulator. Use a physical device to validate crash reporting."
                            : "Physical device detected. This is required for the closest-to-production crash reporting behavior."
                    )

                    CrashEnvironmentInfoRow(
                        title: "Debugger",
                        badge: snapshot.isDebuggerAttached ? "Attached" : "Detached",
                        badgeColor: snapshot.isDebuggerAttached ? Theme.warning : Theme.primary,
                        detail: snapshot.isDebuggerAttached
                            ? "A debugger is attached. Crash reporting can still work, but some monitors are disabled and some crashes look different than in release."
                            : "No debugger attached. Crash behavior should be closer to what users see in production."
                    )

                    CrashEnvironmentInfoRow(
                        title: "Build",
                        badge: snapshot.isReleaseBuild ? "Release" : "Debug",
                        badgeColor: snapshot.isReleaseBuild ? Theme.primary : Theme.warning,
                        detail: snapshot.isReleaseBuild
                            ? "Release mode is active."
                            : "Debug mode is active. Some crash presentations can differ from release."
                    )

                    CrashEnvironmentInfoRow(
                        title: "Capture runtime",
                        badge: snapshot.runtimeCrashReportingState.badge,
                        badgeColor: snapshot.runtimeCrashReportingState.badgeColor,
                        detail: snapshot.runtimeCrashReportingState.message(
                            clientConfigurationEnabled: snapshot.isFatalIssueReportingEnabled
                        )
                    )
                }
            }
        }
    }

    private var metricKitSection: some View {
        let count = self.viewModel.recentCrashDiagnostics.count

        return PanelSection(
            title: "Recent crashes (MetricKit)",
            subtitle: "This section reflects crashes observed from the previous run (Catpure SDK should receive the same)"
        ) {
            NavigationLink(
                destination: MetrickKitCrashDiagnosticsView(records: self.viewModel.recentCrashDiagnostics)
            ) {
                PanelRow(
                    title: "View recent MetricKit diagnostics",
                    subtitle: count == 0
                        ? "No crash diagnostics were delivered on this launch yet. Crash the app on a physical device, then relaunch."
                        : "Open the \(count) crash diagnostic\(count == 1 ? "" : "s") delivered on this launch.",
                    badge: "\(count)",
                    badgeColor: count == 0 ? Theme.textSecondary : Theme.primary,
                    showsChevron: true
                )
            }
            .buttonStyle(PressableCardButtonStyle())
        }
    }

    private func overallStatus(for snapshot: CrashEnvironmentSnapshot) -> CrashOverallStatus {
        if snapshot.isSimulator {
            return CrashOverallStatus(
                badge: "Unavailable",
                color: Theme.danger,
                background: Theme.danger.opacity(0.1),
                message: "You're on the simulator, so Capture fatal issue reporting will not work here. Move to a physical device before validating crash reporting."
            )
        }

        if !snapshot.isFatalIssueReportingEnabled {
            return CrashOverallStatus(
                badge: "Disabled",
                color: Theme.warning,
                background: Theme.warning.opacity(0.1),
                message: "Fatal issue reporting is disabled in the client configuration for this build, so crash reporting is intentionally turned off."
            )
        }

        switch snapshot.runtimeCrashReportingState {
        case .disabled:
            return CrashOverallStatus(
                badge: "Runtime off",
                color: Theme.warning,
                background: Theme.warning.opacity(0.1),
                message: "The cached runtime config currently has crash reporting disabled."
            )
        case .invalid:
            return CrashOverallStatus(
                badge: "Runtime invalid",
                color: Theme.warning,
                background: Theme.warning.opacity(0.1),
                message: "The demo found a cached runtime config, but it could not parse it cleanly."
            )
        case .missing:
            break
        case .enabled:
            break
        }

        if snapshot.isDebuggerAttached {
            return CrashOverallStatus(
                badge: "Limited",
                color: Theme.warning,
                background: Theme.warning.opacity(0.1),
                message: "This is a debugger-attached run. Crash reporting can still work, but some monitors are disabled and reports may differ from release."
            )
        }

        if !snapshot.isReleaseBuild {
            return CrashOverallStatus(
                badge: "Debug build",
                color: Theme.warning,
                background: Theme.warning.opacity(0.1),
                message: "This is a physical device without a debugger, but still a debug build. Prefer a release build for the closest production crash shape."
            )
        }

        if snapshot.runtimeCrashReportingState == .enabled {
            return CrashOverallStatus(
                badge: "Ideal",
                color: Theme.primary,
                background: Theme.primary.opacity(0.1),
                message: "Physical device, release build, no debugger attached, and cached runtime config enables crash reporting. This is the best validation environment."
            )
        }

        return CrashOverallStatus(
            badge: "Waiting",
            color: Theme.secondary,
            background: Theme.secondary.opacity(0.1),
            message: "The demo is running on a physical device, but it has not observed cached runtime crash-reporting config yet. Once runtime config is written locally, this section can confirm whether crash reporting is enabled."
        )
    }
}

private extension CrashesView {
    struct CrashOverallStatus {
        let badge: String
        let color: Color
        let background: Color
        let message: String
    }
}

private struct MetrickKitCrashDiagnosticsView: View {
    let records: [StoredCrashDiagnostic]

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .medium
        return formatter
    }()

    var body: some View {
        PanelScreen {
            PanelSection(
                title: "MetricKit diagnostics",
                subtitle: "These are the crash diagnostics delivered on this launch and persisted by the demo app."
            ) {
                if self.records.isEmpty {
                    PanelCard(background: Theme.surface) {
                        Text("No crash diagnostics have been delivered on this launch yet.")
                            .font(.subheadline)
                            .foregroundColor(Theme.textSecondary)
                    }
                } else {
                    ForEach(self.records) { record in
                        NavigationLink(destination: CrashDiagnosticDetailView(record: record)) {
                            PanelRow(
                                title: record.summary,
                                subtitle: self.subtitle(for: record),
                                badge: record.signalName,
                                badgeColor: Theme.secondary,
                                showsChevron: true
                            )
                        }
                        .buttonStyle(PressableCardButtonStyle())
                    }
                }
            }
        }
        .navigationTitle("Recent crashes")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func subtitle(for record: StoredCrashDiagnostic) -> String {
        var parts = [MetrickKitCrashDiagnosticsView.dateFormatter.string(from: record.receivedAt)]

        if let terminationReason = record.terminationReason, !terminationReason.isEmpty {
            parts.append(terminationReason)
        } else if let exceptionType = record.exceptionType {
            parts.append(record.exceptionDescription(type: exceptionType))
        }

        return parts.joined(separator: " • ")
    }
}

private struct CrashDiagnosticDetailView: View {
    let record: StoredCrashDiagnostic

    var body: some View {
        PanelScreen {
            PanelSection(
                title: "Crash detail",
                subtitle: "Heavy fields like the call stack tree are only rendered here."
            ) {
                PanelCard(background: Color.black.opacity(0.24)) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(self.record.summary)
                            .font(.headline)
                            .foregroundColor(Theme.textPrimary)

                        if let terminationReason = self.record.terminationReason, !terminationReason.isEmpty {
                            CrashDiagnosticFieldView(title: "Termination reason", value: terminationReason)
                        }

                        if let exceptionType = self.record.exceptionType {
                            CrashDiagnosticFieldView(
                                title: "Exception",
                                value: self.record.exceptionDescription(type: exceptionType)
                            )
                        }

                        if let exceptionCode = self.record.exceptionCode {
                            CrashDiagnosticFieldView(title: "Exception code", value: "\(exceptionCode)")
                        }

                        if let signalNumber = self.record.signalNumber {
                            CrashDiagnosticFieldView(
                                title: "Signal",
                                value: self.record.signalName.map { "\($0) (\(signalNumber))" } ?? "\(signalNumber)"
                            )
                        }
                    }
                }

                DisclosureGroup("Call stack tree") {
                    CrashDiagnosticFieldView(title: "Call stack tree", value: self.record.callStackTree)
                        .padding(.top, 12)
                }
                .foregroundColor(Theme.textPrimary)
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(Theme.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Theme.border, lineWidth: 1)
                )

                DisclosureGroup("Raw diagnostic") {
                    CrashDiagnosticFieldView(title: "Raw diagnostic", value: self.record.rawDiagnostic)
                        .padding(.top, 12)
                }
                .foregroundColor(Theme.textPrimary)
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(Theme.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Theme.border, lineWidth: 1)
                )
            }
        }
        .navigationTitle("Crash detail")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct CrashEnvironmentInfoRow: View {
    let title: String
    let badge: String
    let badgeColor: Color
    let detail: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center, spacing: 8) {
                Text(self.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(Theme.textPrimary)
                PanelBadge(text: self.badge, color: self.badgeColor)
            }

            Text(self.detail)
                .font(.footnote)
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct CrashDiagnosticFieldView: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(self.title.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundColor(Theme.textSecondary)

            Text(self.value)
                .font(.system(.footnote, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
                .textSelection(.enabled)
        }
    }
}
