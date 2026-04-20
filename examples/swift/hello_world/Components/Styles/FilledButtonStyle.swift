//
//  FilledButtonStyle.swift
//  Capture
//
//  Created by Ariel Demarco on 18/04/2026.
//


import SwiftUI

struct FilledButtonStyle: ButtonStyle {
    let fill: Color
    let foreground: Color

    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        configuration.label
            .font(.headline.weight(.semibold))
            .foregroundColor(self.foreground)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(self.fill.opacity(configuration.isPressed ? 0.82 : 1.0))
            )
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
    }
}