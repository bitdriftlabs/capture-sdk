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

            PanelSection(
                title: "Session strategy",
                subtitle: "Applied on the next app launch."
            ) {
                PanelCard {
                    Toggle("Use fixed session strategy", isOn: self.$configuration.fixedSessionStrategy)
                }

                if self.configuration.fixedSessionStrategy {
                    Text("The session ID remains fixed until you start a new session or restart the app.")
                        .font(.footnote)
                        .foregroundColor(Theme.textSecondary)
                } else {
                    PanelCard {
                        PanelInputField(
                            title: "Inactivity threshold (minutes)",
                            placeholder: "30",
                            text: self.$configuration.inactivityThresholdMins
                        )
                        .keyboardType(.numberPad)
                    }

                    Text("The session ID changes after \(Configuration.resolvedInactivityThresholdMins) minutes without SDK activity and persists across app restarts.")
                        .font(.footnote)
                        .foregroundColor(Theme.textSecondary)
                }
            }

            PanelSection(
                title: "Web view instrumentation",
                subtitle: "Applied on the next app launch."
            ) {
                PanelCard {
                    Toggle("Instrument web views manually", isOn: self.$configuration.webViewManualInstrumentation)
                }

                if self.configuration.webViewManualInstrumentation {
                    Text("Manual instrumentation will be used starting from the next app launch.")
                        .font(.footnote)
                        .foregroundColor(Theme.textSecondary)
                } else {
                    Text("Automatic swizzling will be used starting from the next app launch.")
                        .font(.footnote)
                        .foregroundColor(Theme.textSecondary)
                }
            }

            PanelCard {
                Text("Restart the app after changing configuration so the SDK is recreated with the updated settings.")
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary)
            }
        }
        .navigationTitle("Configuration")
    }
}
