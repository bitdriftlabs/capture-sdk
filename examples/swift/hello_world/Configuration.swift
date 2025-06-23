// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Combine
import Foundation

final class Configuration: ObservableObject {
    @Published var apiURL: String
    @Published var apiKey: String

    private var subscriptions = Set<AnyCancellable>()

    static var storedAPIURL: String {
        get { UserDefaults.standard.string(forKey: "apiURL") ?? "https://api.bitdrift.io" }
        set { UserDefaults.standard.setValue(newValue, forKey: "apiURL") }
    }

    static var storedAPIKey: String? {
        get { UserDefaults.standard.string(forKey: "apiKey") }
        set { UserDefaults.standard.setValue(newValue, forKey: "apiKey") }
    }

    init() {
        self.apiURL = Self.storedAPIURL
        self.apiKey = Self.storedAPIKey ?? ""

        $apiURL
            .sink(receiveValue: { Self.storedAPIURL = $0 })
            .store(in: &self.subscriptions)
        $apiKey
            .sink(receiveValue: { Self.storedAPIKey = $0 })
            .store(in: &self.subscriptions)
    }
}
