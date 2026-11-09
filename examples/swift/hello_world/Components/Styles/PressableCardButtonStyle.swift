// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct PressableCardButtonStyle: ButtonStyle {
    let cornerRadius: CGFloat

    init(cornerRadius: CGFloat = 20) {
        self.cornerRadius = cornerRadius
    }

    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        FeedbackStyledContent(
            configuration: configuration,
            cornerRadius: self.cornerRadius
        )
    }
}

private struct FeedbackStyledContent: View {
    let configuration: ButtonStyleConfiguration
    let cornerRadius: CGFloat

    var body: some View {
        self.configuration.label
            .overlay(
                RoundedRectangle(cornerRadius: self.cornerRadius)
                    .fill(Color.white.opacity(self.configuration.isPressed ? 0.2 : 0.0))
            )
            .overlay(
                RoundedRectangle(cornerRadius: self.cornerRadius)
                    .stroke(Color.white.opacity(self.configuration.isPressed ? 0.2 : 0.0), lineWidth: 1)
            )
            .scaleEffect(self.configuration.isPressed ? 0.95: 1.0)
            .opacity(self.configuration.isPressed ? 0.9 : 1.0)
            .brightness(self.configuration.isPressed ? 0.1 : 0.0)
            .animation(.easeOut(duration: 0.2), value: self.configuration.isPressed)
    }
}
