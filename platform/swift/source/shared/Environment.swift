// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

enum Environment {
    private static var mockedIsRunningTests: Bool?

    /// Mocks the value returned by `isRunningTests` property. Used for tests purposes only.
    /// Allows to test emission of ANR events in a test environment.
    ///
    /// - parameter isRunningTests: The value to return from `isRunningTests` property.
    static func mockIsRunningTests(_ isRunningTests: Bool) {
        self.mockedIsRunningTests = isRunningTests
    }

    /// Reverts the effect of mocking operations. Used for tests purposes only.
    static func unmock() {
        self.mockedIsRunningTests = nil
    }

    /// Indicates whether the currently running app is an app extension.
    private(set) static var isAppExtension: Bool = // swiftlint:disable:next line_length
        // From Apple's documentation (https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/ExtensionCreation.html):
        // > When you build an extension based on an Xcode template, you get an extension bundle
        // > that ends in .appex.
        Bundle.main.bundlePath.hasSuffix(".appex")

    /// Indicates whether the code is being executed as part of a test run.
    private(set) static var isRunningTests: Bool =
        /// This environment variable is present only when code is executed from within a test host.
        Self.mockedIsRunningTests
        ?? (ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil)
}
