// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import SwiftUI
import WebKit

struct WebView: View {
    @FocusState private var isAddressFieldFocused: Bool
    @StateObject private var browser = WebViewModel()
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 14) {
                WebViewSearchBar(
                    addressText: self.$browser.addressText,
                    currentURLText: self.browser.currentURLText,
                    isAddressFieldFocused: self.$isAddressFieldFocused,
                    onSubmit: {
                        self.isAddressFieldFocused = false
                        self.browser.loadSubmittedText()
                    },
                    onReset: { self.browser.resetAddressBar() }
                )

                WebViewBookmarkPages(
                    pages: self.browser.startupPages,
                    selectedPageID: self.browser.selectedStartupPageID,
                    onSelect: { page in
                        self.isAddressFieldFocused = false
                        self.browser.load(page: page)
                    }
                )

                WebViewControls(
                    canGoBack: self.browser.canGoBack,
                    canGoForward: self.browser.canGoForward,
                    pageTitle: self.browser.pageTitle,
                    pageCaption: self.browser.pageCaption,
                    onBack: { self.browser.goBack() },
                    onForward: { self.browser.goForward() },
                    onReload: { self.browser.reload() }
                )

                if let errorMessage = self.browser.lastErrorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundColor(Theme.warning)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 12)

            ZStack(alignment: .top) {
                WKWebViewBridge(webView: self.browser.wkWebView)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                if self.browser.isLoading {
                    ProgressView(value: self.browser.estimatedProgress)
                        .tint(Theme.primary)
                        .padding(.horizontal, 16)
                        .padding(.top, 10)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .ignoresSafeArea(edges: .bottom)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.background.ignoresSafeArea(edges: .bottom))
    }
}

private struct WKWebViewBridge: UIViewRepresentable {
    let webView: WKWebView
    
    func makeUIView(context _: Context) -> WKWebView {
        return self.webView
    }
    
    func updateUIView(_: WKWebView, context _: Context) {}
}
