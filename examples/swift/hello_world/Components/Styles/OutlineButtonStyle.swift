// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct OutlineButtonStyle: ButtonStyle {
    let stroke: Color
    let foreground: Color
    let background: Color

    init(
        stroke: Color = Theme.primary,
        foreground: Color = Theme.textPrimary,
        background: Color = .clear
    ) {
        self.stroke = stroke
        self.foreground = foreground
        self.background = background
    }

    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        OutlineFeedbackLabel(
            configuration: configuration,
            stroke: self.stroke,
            foreground: self.foreground,
            background: self.background
        )
    }
}

private struct OutlineFeedbackLabel: View {
    let configuration: ButtonStyleConfiguration
    let stroke: Color
    let foreground: Color
    let background: Color

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
                    .fill(self.background)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(self.stroke.opacity(self.isActive ? 0 : 0.8), lineWidth: 1)
            )
            .scaleEffect(self.isActive ? 0.95 : 1.0)
            .opacity(self.isEnabled ? (self.isActive ? 0.9 : 1.0) : 0.55)
            .animation(.easeOut(duration: 0.1), value: self.isActive)
    }
}
