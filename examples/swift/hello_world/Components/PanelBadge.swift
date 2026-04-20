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
            .background(self.color.opacity(0.14))
            .clipShape(Capsule())
    }
}