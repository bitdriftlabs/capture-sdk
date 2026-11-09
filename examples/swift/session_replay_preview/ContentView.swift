// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import SwiftUI

struct ContentView: View {
    private let replayPreviewClient = ReplayPreviewClient(
        hostname: "localhost",
        port: 3001
    )

    private var navigationController: UINavigationController?

    init(navigationController: UINavigationController?) {
        self.navigationController = navigationController
        self.replayPreviewClient.start()
    }

    var body: some View {
        VStack {
            Spacer()
            VStack {
                Button(action: {
                    let controller = UIHostingController(rootView: SwiftUIControlsPreviewView())
                    self.navigationController?.pushViewController(controller, animated: true)
                }) {
                    Text("SwiftUI Controls Preview")
                        .frame(maxWidth: .infinity)
                }
                Button(action: {
                    self.navigationController?.pushViewController(
                        UIKitControlsPreviewViewController(),
                        animated: true
                    )
                }) {
                    Text("UIKit Controls Preview")
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(20)
            .buttonStyle(.bordered)
            Spacer()
        }
        .padding(5)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView(navigationController: nil)
    }
}
