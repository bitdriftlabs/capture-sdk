// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct CommandDetailView: View {
    let command: CommandDefinition
    @ObservedObject var viewModel: CommandsViewModel

    var body: some View {
        PanelScreen {
            PanelSection(title: "Registration", subtitle: self.command.description) {
                PanelCard {
                    Toggle("Registered", isOn: self.registrationBinding)
                        .tint(Theme.primary)
                }

                if let error = self.viewModel.registrationErrors[self.command.key] {
                    Text(error)
                        .font(.footnote)
                        .foregroundColor(Theme.danger)
                }
            }

            PanelSection(title: "Recent invocations") {
                if self.invocations.isEmpty {
                    PanelCard {
                        Text("No invocations received yet.")
                            .font(.subheadline)
                            .foregroundColor(Theme.textSecondary)
                    }
                } else {
                    ForEach(self.invocations) { invocation in
                        CommandInvocationView(invocation: invocation)
                    }
                }
            }
        }
        .navigationTitle(self.command.key)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button("Refresh") {
                self.viewModel.refreshInvocations(for: self.command.key)
            }
        }
        .onAppear {
            self.viewModel.refreshInvocations(for: self.command.key)
        }
    }

    private var invocations: [CommandInvocationRecord] {
        self.viewModel.invocationsByKey[self.command.key, default: []]
    }

    private var registrationBinding: Binding<Bool> {
        Binding(
            get: { self.viewModel.isRegistered(self.command.key) },
            set: { self.viewModel.setRegistered($0, for: self.command.key) }
        )
    }
}
