// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct CommandsView: View {
    @ObservedObject var viewModel: CommandsViewModel

    var body: some View {
        PanelScreen {
            PanelSection(
                title: "Custom commands",
                subtitle: "Open a command to register it or inspect its recent invocations."
            ) {
                ForEach(self.viewModel.customCommands) { command in
                    NavigationLink(destination: CommandDetailView(command: command, viewModel: self.viewModel)) {
                        PanelRow(
                            title: command.key,
                            subtitle: command.description,
                            badge: self.viewModel.isRegistered(command.key) ? "Registered" : "Unregistered",
                            badgeColor: self.viewModel.isRegistered(command.key) ? Theme.primary : Theme.textSecondary,
                            showsChevron: true
                        )
                    }
                    .buttonStyle(PressableCardButtonStyle())
                }
            }

            PanelSection(
                title: "Built-in commands",
                subtitle: "These are provided by the SDK and are enabled or disabled from the console."
            ) {
                ForEach(self.viewModel.builtInCommands) { command in
                    PanelRow(
                        title: command.key,
                        subtitle: command.description,
                        )
                }
            }
        }
        .navigationTitle("Commands")
        .navigationBarTitleDisplayMode(.inline)
    }
}
