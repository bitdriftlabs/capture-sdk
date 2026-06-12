// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import UIKit

@UIApplicationMain
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _: UIApplication,
        didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        Self.backupKSCrashReport()
        Theme.applyNavigationAppearance()
        return true
    }

    // Must run before SceneDelegate initializes the SDK, which processes and deletes lastCrash.bjn.
    private static func backupKSCrashReport() {
        let fm = FileManager.default
        guard
            let appSupport = try? fm.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: false),
            let documents = fm.urls(for: .documentDirectory, in: .userDomainMask).first
        else { return }

        let source = appSupport.appendingPathComponent("bitdrift_capture/reports/kscrash/lastCrash.bjn")
        guard fm.fileExists(atPath: source.path) else { return }

        let timestamp = Int(Date().timeIntervalSince1970)
        let dest = documents.appendingPathComponent("kscrash_\(timestamp).bjn")
        try? fm.copyItem(at: source, to: dest)
    }

    func application(
        _: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options _: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }
}
