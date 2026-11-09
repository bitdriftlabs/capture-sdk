// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct CommandsPanelView: View {
    @ObservedObject var viewModel: CommandsViewModel

    var body: some View {
        PanelSection(
            title: "Commands",
            subtitle: "Register sample commands and inspect invocations received from the console."
        ) {
            NavigationLink(destination: CommandsView(viewModel: self.viewModel)) {
                PanelRow(
                    title: "Commands",
                    subtitle: "\(self.viewModel.registeredKeys.count) of \(self.viewModel.customCommands.count) registered",
                    showsChevron: true
                )
            }
            .buttonStyle(PressableCardButtonStyle())
        }
    }
}
