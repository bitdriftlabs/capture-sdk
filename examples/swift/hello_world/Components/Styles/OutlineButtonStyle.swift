import SwiftUI

struct OutlineButtonStyle: ButtonStyle {
    let stroke: Color
    let foreground: Color
    let background: Color

    init(
        stroke: Color,
        foreground: Color,
        background: Color = .clear
    ) {
        self.stroke = stroke
        self.foreground = foreground
        self.background = background
    }

    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        configuration.label
            .font(.headline.weight(.semibold))
            .foregroundColor(self.foreground)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(self.background.opacity(configuration.isPressed ? 0.82 : 1.0))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(self.stroke.opacity(configuration.isPressed ? 0.82 : 1.0), lineWidth: 1)
            )
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
    }
}