// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct FilledButtonStyle: ButtonStyle {
    let fill: Color
    let foreground: Color

    init(fill: Color = Theme.primary, foreground: Color = Theme.textPrimary) {
        self.fill = fill
        self.foreground = foreground
    }

    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        FilledFeedbackLabel(
            configuration: configuration,
            fill: self.fill,
            foreground: self.foreground
        )
    }
}

private struct FilledFeedbackLabel: View {
    let configuration: ButtonStyleConfiguration
    let fill: Color
    let foreground: Color

    @Environment(\.isEnabled)
    private var isEnabled

    private var isActive: Bool {
        self.configuration.isPressed
    }

    var body: some View {
        self.configuration.label
            .font(.headline.weight(.semibold))
            .foregroundColor(self.foreground)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, minHeight: 24)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 18)
                    .fill(self.fill)
            )
            .scaleEffect(self.isActive ? 0.95 : 1.0)
            .opacity(self.isEnabled ? (self.isActive ? 0.9 : 1.0) : 0.55)
            .brightness(self.isActive ? 0.1 : 0.0)
            .animation(.easeOut(duration: 0.1), value: self.isActive)
    }
}
