// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct ConfigurationView: View {
    @StateObject var configuration = Configuration()

    var body: some View {
        Form {
            Section(header: Text("API URL")) {
                TextField("Enter API URL", text: $configuration.apiURL)
                    .autocapitalization(.none)
            }
            Section(header: Text("API Key")) {
                TextField("Enter API Key", text: $configuration.apiKey)
                    .autocapitalization(.none)
            }
        }

        Spacer()

        Text("The app needs to be restarted for any configuration change to take effect.")
            .font(.caption2)
            .padding(EdgeInsets(top: 10, leading: 10, bottom: 20, trailing: 10))
    }
}
