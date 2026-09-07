// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI
import UIKit

@objc(SceneDelegate)
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo _: UISceneSession,
        options _: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        guard let appDelegate = UIApplication.shared.delegate as? AppDelegate else { return }

        let startupCrashStorage = StartupCrashStorage()
        let crashRegistry = CrashRegistry(startupStorage: startupCrashStorage)
        let crashPanelViewModel = CrashPanelViewModel(crashRegistry: crashRegistry)
        crashPanelViewModel.refreshEnvironment()

        let mainViewFactory = MainViewFactory(
            loggerCustomer: appDelegate.loggerCustomer,
            crashPanelViewModel: crashPanelViewModel,
            launchArguments: ProcessInfo.processInfo.arguments
        )
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = UIHostingController(rootView: mainViewFactory.makeView())
        window.makeKeyAndVisible()
        self.window = window
    }
}
