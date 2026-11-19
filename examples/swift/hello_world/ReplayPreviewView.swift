// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import MapKit
import SwiftUI
import WebKit

struct ReplayPreviewView: View {
    @State private var toggleOff = false
    @State private var toggleOn = true
    @State private var textFieldText = "TextField value"
    @State private var textEditorText = "TextEditor\nmultiline content"
    @State private var selectedDate = Date()
    @State private var pickerSelection = "A"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                PanelSection(title: "Label") {
                    VStack(spacing: 8) {
                        self.row(Text("Plain Text element"))
                        self.row(Label("Label with SF icon", systemImage: "heart.fill"))
                        self.row(Text("Bold text").bold())
                        self.row(Text("Secondary text").foregroundStyle(Theme.textSecondary))
                    }
                }

                PanelSection(title: "Button") {
                    VStack(spacing: 8) {
                        self.row(Button("Plain button") {})
                        self.row(Button("Bordered button") {}.buttonStyle(.bordered))
                        self.row(Button("Bordered prominent") {}.buttonStyle(.borderedProminent))
                        // swiftlint:disable:next use_static_string_url_init force_unwrapping
                        self.row(Link("Link element", destination: URL(string: "https://bitdrift.io")!))
                    }
                }

                PanelSection(title: "Text Input") {
                    VStack(spacing: 8) {
                        self.row(
                            TextField("Placeholder", text: self.$textFieldText)
                                .foregroundStyle(Theme.textPrimary)
                        )
                        self.row(
                            TextEditor(text: self.$textEditorText)
                                .foregroundStyle(Theme.textPrimary)
                                .frame(height: 70)
                        )
                    }
                }

                PanelSection(title: "Image") {
                    VStack(spacing: 8) {
                        self.row(
                            Image(systemName: "star.fill")
                                .resizable()
                                .frame(width: 36, height: 36)
                                .foregroundStyle(Theme.primary)
                        )
                        self.row(
                            Image(systemName: "photo")
                                .resizable()
                                .scaledToFit()
                                .frame(height: 60)
                                .foregroundStyle(Theme.textSecondary)
                        )
                    }
                }

                PanelSection(title: "Switch") {
                    VStack(spacing: 8) {
                        self.row(Toggle("Toggle off", isOn: self.$toggleOff))
                        self.row(Toggle("Toggle on", isOn: self.$toggleOn))
                    }
                }

                PanelSection(title: "Picker") {
                    VStack(spacing: 8) {
                        self.row(
                            Picker("Segmented", selection: self.$pickerSelection) {
                                Text("A").tag("A")
                                Text("B").tag("B")
                                Text("C").tag("C")
                            }
                            .pickerStyle(.segmented)
                        )
                        self.row(
                            DatePicker("Date", selection: self.$selectedDate, displayedComponents: .date)
                                .foregroundStyle(Theme.textPrimary)
                        )
                    }
                }

                if #available(iOS 17, *) {
                    PanelSection(title: "Map") {
                        Map()
                            .frame(height: 150)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Theme.border, lineWidth: 1)
                            )
                    }
                }

                PanelSection(title: "WebView") {
                    ReplayPreviewWebView()
                        .frame(height: 80)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Theme.border, lineWidth: 1)
                        )
                }

                PanelSection(title: "Navigation (chevron)") {
                    VStack(spacing: 8) {
                        NavigationLink(destination: Text("Detail").foregroundStyle(Theme.textPrimary)) {
                            PanelRow(title: "Row with chevron", subtitle: "Navigates to a detail screen", showsChevron: true)
                        }
                        .buttonStyle(PressableCardButtonStyle())
                        NavigationLink(destination: Text("Detail").foregroundStyle(Theme.textPrimary)) {
                            PanelRow(title: "Another row", showsChevron: true)
                        }
                        .buttonStyle(PressableCardButtonStyle())
                    }
                }

                PanelSection(title: "Transparent View") {
                    ZStack {
                        Theme.secondary.opacity(0.15)
                            .frame(height: 70)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Theme.border, lineWidth: 1)
                            )
                        Theme.primary.opacity(0.3)
                            .frame(width: 100, height: 40)
                            .cornerRadius(8)
                    }
                }
            }
            .padding(20)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle("Replay Preview")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func row<Content: View>(_ content: Content) -> some View {
        content
            .foregroundStyle(Theme.textPrimary)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(Theme.surface))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border, lineWidth: 1))
    }
}

private struct ReplayPreviewWebView: UIViewRepresentable {
    func makeUIView(context _: Context) -> WKWebView {
        let webView = WKWebView()
        webView.loadHTMLString("<html><body style='background:#1a1a1a;color:#fff;font-family:sans-serif;padding:12px'><p>WebView content</p></body></html>", baseURL: nil)
        return webView
    }

    func updateUIView(_: WKWebView, context _: Context) {}
}
