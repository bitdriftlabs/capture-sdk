// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI
import UIKit

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

    @State private var selectedCrashAction: CrashActionSelection?

    var body: some View {
        PanelScreen {
            self.environmentSection
            self.startupCrashSection
            self.metricKitSection

            ForEach(CrashCategory.allCases, id: \.rawValue) { category in
                PanelSection(
                    title: category.rawValue,
                    subtitle: category.subtitle
                ) {
                    let crashes = self.viewModel.crashes(in: category)
                    ForEach(Array(crashes.enumerated()), id: \.offset) { item in
                        let crash = item.element
                        Button(action: {
                            self.selectedCrashAction = CrashActionSelection(crash: crash)
                        }) {
                            PanelRow(
                                title: crash.title,
                                subtitle: crash.crashDescription,
                                badge: self.viewModel.isScheduledStartupCrash(crash)
                                    ? "Startup"
                                    : (crash.supportsStartupTrigger ? nil : "Now only"),
                                badgeColor: self.viewModel.isScheduledStartupCrash(crash)
                                    ? Theme.warning
                                    : Theme.textSecondary,
                                showsChevron: true
                            )
                        }
                        .buttonStyle(PressableCardButtonStyle())
                    }
                }
            }
        }
        .navigationTitle("Crashes")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            self.viewModel.refreshEnvironment()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            self.viewModel.refreshEnvironment()
        }
        .confirmationDialog(
            self.selectedCrashAction?.crash.title ?? "Crash actions",
            isPresented: Binding(
                get: { self.selectedCrashAction != nil },
                set: { if !$0 { self.selectedCrashAction = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let selectedCrashAction {
                Button("Trigger now", role: .destructive) {
                    selectedCrashAction.crash.trigger()
                }

                if selectedCrashAction.crash.supportsStartupTrigger {
                    Button("Crash on next launch") {
                        self.viewModel.scheduleStartupCrash(selectedCrashAction.crash)
                    }

                    if self.viewModel.isScheduledStartupCrash(selectedCrashAction.crash) {
                        Button("Clear scheduled startup crash") {
                            self.viewModel.cancelScheduledStartupCrash()
                        }
                    }
                }
            }
        } message: {
            Text(
                selectedCrashAction?.crash.supportsStartupTrigger == true
                    ? "Choose whether to trigger this crash now or before Capture SDK initializes on the next launch."
                    : "This crash only reproduces faithfully after app startup, so next-launch scheduling is unavailable."
            )
        }
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

                    CrashEnvironmentInfoRow(
                        title: "KSCrash cache",
                        badge: snapshot.kscrashCacheState.badge,
                        badgeColor: snapshot.kscrashCacheState.badgeColor,
                        detail: snapshot.kscrashCacheState.message
                    )
                }
            }
        }
    }

    private var metricKitSection: some View {
        let count = self.viewModel.recentCrashDiagnostics.count

        return PanelSection(
            title: "Recent crashes (MetricKit)",
            subtitle: "This section reflects crashes observed from the previous run. Capture should receive the same diagnostic set."
        ) {
            NavigationLink(
                destination: MetricKitCrashDiagnosticsView(records: self.viewModel.recentCrashDiagnostics)
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

    private var startupCrashSection: some View {
        PanelSection(
            title: "Next launch crash",
            subtitle: "Schedule one crash for the next launch. It will fire before main() and before Capture SDK initialization."
        ) {
            PanelCard(background: Theme.surface) {
                Text(self.viewModel.scheduledStartupCrashTitle ?? "No startup crash scheduled.")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(Theme.textPrimary)

                Text("Only crashes that reproduce faithfully before main() are eligible for startup scheduling.")
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                if self.viewModel.scheduledStartupCrashTitle != nil {
                    Button(action: {
                        self.viewModel.cancelScheduledStartupCrash()
                    }) {
                        Text("Clear scheduled crash")
                    }
                    .buttonStyle(
                        OutlineButtonStyle(
                            stroke: Theme.warning.opacity(0.8),
                            foreground: Theme.textPrimary,
                            background: Theme.warning.opacity(0.1)
                        )
                    )
                }
            }
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

    struct CrashActionSelection {
        let crash: any Crash
    }
}

private struct MetricKitCrashDiagnosticsView: View {
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
                                badge: record.exceptionName ?? record.signalName,
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
        var parts = [MetricKitCrashDiagnosticsView.dateFormatter.string(from: record.receivedAt)]

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
                PanelCard(background: Color.black.opacity(0.2)) {
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

                CrashDiagnosticLargeTextLink(
                    title: "Call stack tree",
                    subtitle: "Open the full stack trace in a dedicated view.",
                    text: self.record.callStackTree
                )

                CrashDiagnosticLargeTextLink(
                    title: "Raw diagnostic",
                    subtitle: "Open the full MetricKit raw diagnostic payload.",
                    text: self.record.rawDiagnostic
                )
            }
        }
        .navigationTitle("Crash detail")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct CrashDiagnosticLargeTextLink: View {
    let title: String
    let subtitle: String
    let text: String

    @State private var isPreparing = false
    @State private var isShowingDestination = false

    var body: some View {
        ZStack {
            NavigationLink(
                destination: CrashDiagnosticLargeTextView(title: self.title, text: self.text),
                isActive: self.$isShowingDestination
            ) {
                EmptyView()
            }
            .hidden()

            Button(action: self.openDestination) {
                PanelRow(
                    title: self.title,
                    subtitle: self.subtitle,
                    badgeColor: Theme.secondary,
                    showsChevron: !self.isPreparing
                )
            }
            .buttonStyle(PressableCardButtonStyle())
            .disabled(self.isPreparing)
        }
    }

    private func openDestination() {
        guard !self.isPreparing else {
            return
        }

        self.isPreparing = true
        DispatchQueue.main.async {
            self.isPreparing = false
            self.isShowingDestination = true
        }
    }
}

private struct CrashDiagnosticLargeTextView: View {
    let title: String
    let text: String

    @State private var loadedText: String?
    @State private var isPresentingShareSheet = false

    var body: some View {
        Group {
            if let loadedText {
                LargeCrashTextContainer(text: loadedText)
                    .background(Theme.background.ignoresSafeArea())
            } else {
                ZStack {
                    Theme.background
                        .ignoresSafeArea()

                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(Theme.primary)
                }
            }
        }
        .navigationTitle(self.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button(action: self.copyText) {
                    Image(systemName: "doc.on.doc")
                }
                .disabled(self.loadedText == nil)

                Button(action: { self.isPresentingShareSheet = true }) {
                    Image(systemName: "square.and.arrow.up")
                }
                .disabled(self.loadedText == nil)
            }
        }
        .sheet(isPresented: self.$isPresentingShareSheet) {
            if let loadedText = self.loadedText {
                ActivityViewController(activityItems: [loadedText])
            }
        }
        .task {
            guard self.loadedText == nil else {
                return
            }

            await Task.yield()
            self.loadedText = self.text
        }
    }

    private func copyText() {
        guard let loadedText = self.loadedText else {
            return
        }

        UIPasteboard.general.string = loadedText
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
