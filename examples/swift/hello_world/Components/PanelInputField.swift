// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct PanelInputField: View {
    let title: String
    let placeholder: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(self.title)
                .font(.subheadline.weight(.semibold))
                .foregroundColor(Theme.textPrimary)

            TextField(self.placeholder, text: self.$text)
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .foregroundColor(Theme.textPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Theme.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Theme.border, lineWidth: 1)
                )
        }
    }
}
