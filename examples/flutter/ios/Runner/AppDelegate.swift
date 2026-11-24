// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate {
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        GeneratedPluginRegistrant.register(with: self)
        if let registrar = self.registrar(forPlugin: "CrashChannel") {
            let channel = FlutterMethodChannel(
                name: "io.bitdrift.flutter_example/crash",
                binaryMessenger: registrar.messenger()
            )
            channel.setMethodCallHandler { call, result in
                guard call.method == "nativeCrash" else {
                    result(FlutterMethodNotImplemented)
                    return
                }
                result(nil)
                DispatchQueue.main.async {
                    NSException(
                        name: .internalInconsistencyException,
                        reason: "Test iOS crash from Flutter example",
                        userInfo: nil
                    ).raise()
                }
            }
        }
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}
