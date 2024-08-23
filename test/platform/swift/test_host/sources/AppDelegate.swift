// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import UIKit

@UIApplicationMain
private final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _: UIApplication,
        didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        self.window = UIWindow(frame: UIScreen.main.bounds)
        self.window?.rootViewController = HostViewController()
        self.window?.makeKeyAndVisible()
        return true
    }

    private final class HostViewController: UIViewController {
        override func viewDidLoad() {
            super.viewDidLoad()

            self.view.backgroundColor = .white

            let label = UILabel()
            self.view.addSubview(label)
            label.text = "Test Host"
            label.font = .boldSystemFont(ofSize: 50)
            label.sizeToFit()
            label.center = self.view.center
        }
    }
}
