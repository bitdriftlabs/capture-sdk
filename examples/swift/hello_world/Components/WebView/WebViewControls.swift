// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct WebViewControls: View {
    let canGoBack: Bool
    let canGoForward: Bool
    let pageTitle: String
    let pageCaption: String
    let onBack: () -> Void
    let onForward: () -> Void
    let onReload: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            WebViewControlButton(
                systemImage: "chevron.backward",
                isEnabled: self.canGoBack,
                tint: Theme.textPrimary,
                action: self.onBack
            )
            WebViewControlButton(
                systemImage: "chevron.forward",
                isEnabled: self.canGoForward,
                tint: Theme.textPrimary,
                action: self.onForward
            )
            WebViewControlButton(
                systemImage: "arrow.clockwise",
                isEnabled: true,
                tint: Theme.primary,
                action: self.onReload
            )

            Spacer(minLength: 12)

            VStack(alignment: .trailing, spacing: 2) {
                Text(self.pageTitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)

                Text(self.pageCaption)
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(1)
            }
        }
    }
}
