// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct WebViewControlButton: View {
    let systemImage: String
    let isEnabled: Bool
    let tint: Color
    let action: () -> Void
    
    var body: some View {
        Button(action: self.action) {
            Image(systemName: self.systemImage)
                .font(.subheadline.weight(.semibold))
                .foregroundColor(self.isEnabled ? self.tint : Theme.textSecondary.opacity(0.6))
                .frame(width: 40, height: 40)
                .background(
                    Circle()
                        .fill(self.isEnabled ? Theme.surface : Theme.surface.opacity(0.5))
                )
                .overlay(
                    Circle()
                        .stroke(Theme.border, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(!self.isEnabled)
    }
}
