// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import SwiftUI

struct ContentView: View {
    private var navigationController: UINavigationController?
    private let loggerCustomer: LoggerCustomer

    @State private var currentSessionID: String
    @State private var createdDeviceCode: String = "No Code Generated"
    @State private var selectedLogLevel = LoggerCustomer.LogLevel.info

    init(navigationController: UINavigationController?) {
        self.loggerCustomer = LoggerCustomer()
        self.navigationController = navigationController
        self.currentSessionID = self.loggerCustomer.sessionID ?? "No Session ID"
    }

    var body: some View {
        VStack {
            NavigationLink("Configuration") { ConfigurationView() }
            Spacer()
            VStack {
                Text("ACTIONS")
                HStack {
                    Button(action: { self.loggerCustomer.log(with: self.selectedLogLevel) }) {
                        Text("Log").frame(maxWidth: .infinity)
                    }
                    Picker("Log Level", selection: self.$selectedLogLevel, content: {
                        ForEach(LoggerCustomer.LogLevel.allCases) { level in
                            Text(level.rawValue).tag(level)
                        }
                    })
                }
                Button(action: { self.loggerCustomer.performRandomNetworkRequestUsingDataTask() }) {
                    Text("Perform Random Data/Upload Request").frame(maxWidth: .infinity)
                }
                Button(action: {
                    self.loggerCustomer.simulateSpan()
                }) {
                    Text("Simulate Span Event").frame(maxWidth: .infinity)
                }
                Button(action: {
                    self.loggerCustomer.simulateNavigation()
                }) {
                    Text("Simulate Navigation to Screen").frame(maxWidth: .infinity)
                }
                Button(action: { self.loggerCustomer.setFeatureFlag(flag: "MyFlag", variant: "MyVariant") }) {
                    Text("Set feature flag 'MyFlag' to 'MyVariant'").frame(maxWidth: .infinity)
                }
                Button(action: { self.loggerCustomer.removeFeatureFlag(flag: "MyFlag") }) {
                    Text("Remove feature flag 'MyFlag'").frame(maxWidth: .infinity)
                }
                Button(action: { Thread.sleep(forTimeInterval: 5.0) }) {
                    Text("Simulate ANR (5s)").frame(maxWidth: .infinity)
                }
                Button(action: {
                    let items = [1, 2, 3]
                    print("the fourth item: \(items[3])")
                }) {
                    Text("Swift Assertion Failure").frame(maxWidth: .infinity)
                }
            }
            .modify { view in
                view.buttonStyle(.bordered)
            }
            Spacer()
            VStack {
                VStack {
                    Button(action: {
                        self.loggerCustomer.createTemporaryDeviceCode(completion: { result in
                            switch result {
                            case let .success(code):
                                self.createdDeviceCode = code
                                UIPasteboard.general.string = code
                            case let .failure(error):
                                self.createdDeviceCode = String(describing: error)
                            }
                        })
                    }) {
                        Text("Generate Temporary Device Code").frame(maxWidth: .infinity)
                    }
                    .modify { view in
                        view.buttonStyle(.borderedProminent)
                    }
                    Text(self.createdDeviceCode)
                }
                .padding(EdgeInsets(top: 0, leading: 0, bottom: 10, trailing: 0))
                HStack {
                    Button(action: { UIPasteboard.general.string = self.loggerCustomer.sessionURL }) {
                        Text("Copy Session URL").frame(maxWidth: .infinity)
                    }
                    .modify { view in
                        view.buttonStyle(.borderedProminent)
                    }
                    Button(action: {
                        self.loggerCustomer.startNewSession()
                        self.currentSessionID = self.loggerCustomer.sessionID ?? "No Session ID"
                    }) {
                        Text("Start New Session").frame(maxWidth: .infinity)
                    }
                    .modify { view in
                        view.buttonStyle(.borderedProminent)
                    }
                }
                Text(self.currentSessionID)
            }
        }
        .padding(5)
        .onAppear { self.loggerCustomer.logAppLaunchTTI() }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView(navigationController: nil)
    }
}

extension View {
    func modify(@ViewBuilder _ modifier: (Self) -> some View) -> some View {
        return modifier(self)
    }
}
