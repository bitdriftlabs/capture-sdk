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
        PanelScreen {
            PanelSection(
                title: "Connection",
                subtitle: "These values are stored in UserDefaults and applied on the next app launch."
            ) {
                PanelCard {
                    PanelInputField(
                        title: "API URL",
                        placeholder: "Enter API URL",
                        text: self.$configuration.apiURL
                    )

                    PanelInputField(
                        title: "API key",
                        placeholder: "Enter API key",
                        text: self.$configuration.apiKey
                    )
                }
            }

            PanelCard {
                Text("Restart the app after changing configuration so the SDK is recreated with the updated endpoint and API key.")
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary)
            }
        }
        .navigationTitle("Configuration")
    }
}
