// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

/// A configuration used to configure Capture session replay feature.
public struct SessionReplayConfiguration {
    public var categorizers: [String: AnnotatedView]

    /// Initializes a new session replay configuration.
    ///
    /// - parameter categorizers: Map used to match third-party views to bitdrift session replay view types.
    public init(categorizers: [String: AnnotatedView] = [:]) {
        self.categorizers = categorizers
    }
}
