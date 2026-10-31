// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import SwiftUI

struct PanelBadge: View {
    let text: String
    let color: Color

    var body: some View {
        Text(self.text.uppercased())
            .font(.caption.weight(.semibold))
            .foregroundColor(self.color)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(self.color.opacity(0.2))
            .clipShape(Capsule())
    }
}
