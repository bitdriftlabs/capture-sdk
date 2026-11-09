// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct CommandInvocationView: View {
    let invocation: CommandInvocationRecord

    var body: some View {
        PanelCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(self.invocation.receivedAt.formatted(date: .omitted, time: .standard))
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(Theme.textPrimary)

                self.fields(title: "Arguments", values: self.invocation.arguments)

                switch self.invocation.outcome {
                case let .succeeded(context):
                    self.fields(title: "Result", values: context, emptyValue: "Succeeded")
                case let .failed(message):
                    self.fields(title: "Result", values: ["error": message])
                }
            }
        }
    }

    private func fields(title: String, values: [String: String], emptyValue: String = "None") -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.footnote.weight(.semibold))
                .foregroundColor(Theme.textSecondary)

            if values.isEmpty {
                Text(emptyValue)
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary)
            } else {
                ForEach(values.keys.sorted(), id: \.self) { key in
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(key)
                            .font(.footnote.monospaced())
                            .foregroundColor(Theme.textSecondary)
                        Text(values[key] ?? "")
                            .font(.footnote.monospaced())
                            .foregroundColor(Theme.textPrimary)
                            .textSelection(.enabled)
                    }
                }
            }
        }
    }
}
