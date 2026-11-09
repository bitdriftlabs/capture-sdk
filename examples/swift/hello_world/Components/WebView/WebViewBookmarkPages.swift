// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct WebViewBookmarkPages: View {
    let pages: [WebViewPage]
    let selectedPageID: WebViewPage.ID?
    let onSelect: (WebViewPage) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(self.pages) { page in
                    Button(action: { self.onSelect(page) }) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(page.title)
                                .font(.caption.weight(.semibold))
                                .foregroundColor(Theme.textPrimary)
                                .lineLimit(1)

                            Text(page.addressText)
                                .font(.caption2)
                                .foregroundColor(Theme.textSecondary)
                                .lineLimit(1)
                        }
                        .frame(width: 180, alignment: .leading)
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(page.id == self.selectedPageID ? Theme.primary.opacity(0.2) : Theme.surface)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(
                                    page.id == self.selectedPageID ? Theme.primary.opacity(0.5) : Theme.border,
                                    lineWidth: 1
                                )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}
