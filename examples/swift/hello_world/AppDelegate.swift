// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import SwiftUI
import UIKit

@UIApplicationMain
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _: UIApplication,
        didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        Theme.applyNavigationAppearance()
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = UIHostingController(rootView: createContentView())
        window.makeKeyAndVisible()
        self.window = window

        return true
    }

    private func createContentView() -> some View {
        let startupCrashStorage = StartupCrashStorage()
        let crashRegistry = CrashRegistry(startupStorage: startupCrashStorage)
        let loggerCustomer = LoggerCustomer()
        let crashPanelViewModel = CrashPanelViewModel(crashRegistry: crashRegistry)
        crashPanelViewModel.refreshEnvironment()
        return ContentView(
            loggerCustomer: loggerCustomer,
            crashPanelViewModel: crashPanelViewModel
        )
    }
}
