// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct PanelSection<Content: View>: View {
    let title: String
    let subtitle: String?
    private let content: Content

    init(
        title: String,
        subtitle: String? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.subtitle = subtitle
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(self.title.uppercased())
                .font(.footnote.weight(.semibold))
                .foregroundColor(Theme.textSecondary)

            if let subtitle {
                Text(subtitle)
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary.opacity(0.9))
            }

            self.content
        }
    }
}
