// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.txt

import SwiftUI

private enum InitialScreen: String {
    case crashes
}

struct MainViewFactory {
    private let loggerCustomer: LoggerCustomer
    private let crashPanelViewModel: CrashPanelViewModel
    private let launchArguments: [String]

    init(
        loggerCustomer: LoggerCustomer,
        crashPanelViewModel: CrashPanelViewModel,
        launchArguments: [String]
    ) {
        self.loggerCustomer = loggerCustomer
        self.crashPanelViewModel = crashPanelViewModel
        self.launchArguments = launchArguments
    }

    @ViewBuilder
    func makeView() -> some View {
        switch self.initialScreen {
        case .crashes:
            NavigationView {
                CrashesView(viewModel: self.crashPanelViewModel)
            }
            .navigationViewStyle(.stack)
            .accentColor(Theme.primary)
        case nil:
            ContentView(
                loggerCustomer: self.loggerCustomer,
                crashPanelViewModel: self.crashPanelViewModel
            )
        }
    }

    private var initialScreen: InitialScreen? {
        guard let screenArgumentIndex = self.launchArguments.firstIndex(of: "-bitdrift-initial-screen"),
              self.launchArguments.indices.contains(self.launchArguments.index(after: screenArgumentIndex))
        else {
            return nil
        }

        return InitialScreen(rawValue: self.launchArguments[self.launchArguments.index(after: screenArgumentIndex)])
    }
}
