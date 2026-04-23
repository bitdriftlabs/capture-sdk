// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI
import WebKit

@MainActor
final class WebViewModel: NSObject, ObservableObject {
    @Published var addressText: String
    @Published private(set) var currentURLText: String
    @Published private(set) var pageTitle: String
    @Published private(set) var isLoading = false
    @Published private(set) var estimatedProgress = 0.0
    @Published private(set) var canGoBack = false
    @Published private(set) var canGoForward = false
    @Published private(set) var lastErrorMessage: String?
    @Published private(set) var selectedStartupPageID: WebViewPage.ID?
    
    let startupPages = WebViewPage.defaults
    let wkWebView: WKWebView
    
    private var observations = [NSKeyValueObservation]()
    
    var pageCaption: String {
        if self.isLoading {
            return "Loading..."
        }
        
        if let host = self.wkWebView.url?.host, !host.isEmpty {
            return host
        }
        
        return self.currentURLText
    }
    
    override init() {
        let initialPage = WebViewPage.defaults[0]
        let configuration = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: configuration)
        
        self.wkWebView = webView
        self.addressText = initialPage.addressText
        self.currentURLText = initialPage.addressText
        self.pageTitle = initialPage.title
        self.selectedStartupPageID = initialPage.id
        
        super.init()
        
        self.wkWebView.navigationDelegate = self
        self.wkWebView.allowsBackForwardNavigationGestures = true
        self.wkWebView.isOpaque = false
        self.wkWebView.backgroundColor = .clear
        self.wkWebView.scrollView.backgroundColor = .clear
        
        self.observeWebView()
        self.load(page: initialPage)
    }
    
    func loadSubmittedText() {
        let submittedText = self.addressText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !submittedText.isEmpty else {
            self.resetAddressBar()
            return
        }
        
        guard let url = self.resolveURL(from: submittedText) else {
            self.lastErrorMessage = "Couldn't open that address. Try a full URL like https://example.com."
            return
        }
        
        self.selectedStartupPageID = self.remotePageID(for: url)
        self.load(url: url)
    }

    func load(page: WebViewPage) {
        self.selectedStartupPageID = page.id
        switch page.source {
        case let .remote(url):
            self.load(url: url)
        case let .inlineHTML(html, baseURL):
            self.loadHTML(html, baseURL: baseURL, addressText: page.addressText)
        }
    }
    
    func goBack() {
        self.lastErrorMessage = nil
        self.wkWebView.goBack()
    }
    
    func goForward() {
        self.lastErrorMessage = nil
        self.wkWebView.goForward()
    }
    
    func reload() {
        self.lastErrorMessage = nil
        self.wkWebView.reload()
    }
    
    func resetAddressBar() {
        self.addressText = self.currentURLText
    }
    
    private func load(url: URL) {
        self.lastErrorMessage = nil
        self.currentURLText = url.absoluteString
        self.addressText = url.absoluteString
        self.wkWebView.load(URLRequest(url: url))
    }

    private func loadHTML(_ html: String, baseURL: URL?, addressText: String) {
        self.lastErrorMessage = nil
        self.currentURLText = addressText
        self.addressText = addressText
        self.wkWebView.loadHTMLString(html, baseURL: baseURL)
    }

    private func remotePageID(for url: URL) -> WebViewPage.ID? {
        return self.startupPages.first(where: { page in
            if case let .remote(pageURL) = page.source {
                return pageURL.absoluteString == url.absoluteString
            }
            return false
        })?.id
    }
    
    private func observeWebView() {
        self.observations = [
            self.wkWebView.observe(\.title, options: [.initial, .new]) { [weak self] webView, _ in
                Task { @MainActor [weak self] in
                    guard let self else {
                        return
                    }

                    let title = webView.title?.trimmingCharacters(in: .whitespacesAndNewlines)
                    if let title, !title.isEmpty {
                        self.pageTitle = title
                    } else {
                        self.pageTitle = "Web demo"
                    }
                }
            },
            self.wkWebView.observe(\.url, options: [.initial, .new]) { [weak self] webView, _ in
                Task { @MainActor [weak self] in
                    guard let self, let url = webView.url else {
                        return
                    }

                    self.currentURLText = url.absoluteString
                    if !self.isLoading {
                        self.addressText = url.absoluteString
                    }
                    if let remotePageID = self.remotePageID(for: url) {
                        self.selectedStartupPageID = remotePageID
                    } else if self.selectedStartupPageID != "demo-page" {
                        self.selectedStartupPageID = nil
                    }
                }
            },
            self.wkWebView.observe(\.estimatedProgress, options: [.initial, .new]) { [weak self] webView, _ in
                Task { @MainActor [weak self] in
                    self?.estimatedProgress = webView.estimatedProgress
                }
            },
            self.wkWebView.observe(\.canGoBack, options: [.initial, .new]) { [weak self] webView, _ in
                Task { @MainActor [weak self] in
                    self?.canGoBack = webView.canGoBack
                }
            },
            self.wkWebView.observe(\.canGoForward, options: [.initial, .new]) { [weak self] webView, _ in
                Task { @MainActor [weak self] in
                    self?.canGoForward = webView.canGoForward
                }
            },
        ]
    }
    
    private func resolveURL(from input: String) -> URL? {
        if input.contains(" ") {
            return WebViewModel.searchURL(for: input)
        }
        
        if let url = URL(string: input), url.scheme != nil {
            return url
        }
        
        if input.contains(".") {
            return URL(string: "https://\(input)")
        }
        
        return WebViewModel.searchURL(for: input)
    }
    
    private static func searchURL(for query: String) -> URL? {
        guard
            let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)
        else {
            return nil
        }
        
        return URL(string: "https://duckduckgo.com/?q=\(encodedQuery)")
    }
}

extension WebViewModel: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didStartProvisionalNavigation _: WKNavigation!) {
        self.lastErrorMessage = nil
        self.isLoading = true
        self.estimatedProgress = webView.estimatedProgress
    }
    
    func webView(_ webView: WKWebView, didFinish _: WKNavigation!) {
        self.isLoading = false
        self.estimatedProgress = 1.0
        if let url = webView.url {
            self.currentURLText = url.absoluteString
            self.addressText = url.absoluteString
        }
    }
    
    func webView(_: WKWebView, didFail _: WKNavigation!, withError error: Error) {
        self.handleNavigationFailure(error)
    }
    
    func webView(
        _: WKWebView,
        didFailProvisionalNavigation _: WKNavigation!,
        withError error: Error
    ) {
        self.handleNavigationFailure(error)
    }
    
    private func handleNavigationFailure(_ error: Error) {
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain, nsError.code == NSURLErrorCancelled {
            return
        }
        
        self.isLoading = false
        self.estimatedProgress = 0.0
        self.lastErrorMessage = nsError.localizedDescription
        self.resetAddressBar()
    }
}

