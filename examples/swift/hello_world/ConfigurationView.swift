import SwiftUI

struct ConfigurationView: View {
    @StateObject var configuration = Configuration()

    var body: some View {
        Text("API URL").frame(maxWidth: .infinity)
        TextField(text: $configuration.apiURL) { Text("Enter API URL") }
            .autocapitalization(.none)

        Text("API Key").frame(maxWidth: .infinity)
        TextField(text: $configuration.apiKey, axis: .vertical) { Text("Enter API Key") }
            .autocapitalization(.none)

        Spacer()

        Text("The app needs to be restarted for any configuration change to take effect.")
            .font(.caption2)
            .padding(EdgeInsets(top: 10, leading: 10, bottom: 20, trailing: 10))
    }
}
