// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct WebViewSearchBar: View {
    @Binding var addressText: String

    let currentURLText: String
    let isAddressFieldFocused: FocusState<Bool>.Binding
    let onSubmit: () -> Void
    let onReset: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(Theme.textSecondary)

                TextField("Search or enter a URL", text: self.$addressText)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .keyboardType(.URL)
                    .submitLabel(.go)
                    .foregroundColor(Theme.textPrimary)
                    .focused(self.isAddressFieldFocused)
                    .onSubmit(self.onSubmit)

                if self.addressText != self.currentURLText {
                    Button(action: self.onReset) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Theme.textSecondary.opacity(0.8))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 18)
                    .fill(Theme.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(Theme.border, lineWidth: 1)
            )

            Button(action: self.onSubmit) {
                Image(systemName: "arrow.right")
                    .font(.headline.weight(.semibold))
                    .foregroundColor(Theme.textPrimary)
                    .frame(width: 46, height: 46)
                    .background(
                        Circle()
                            .fill(Theme.primary)
                    )
            }
            .buttonStyle(.plain)
        }
    }
}
