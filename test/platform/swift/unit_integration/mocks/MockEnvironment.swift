// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture

public class MockEnvironment: AppEnvironment {
    public var isSimulator: Bool

    public init(isSimulator: Bool) {
        self.isSimulator = isSimulator
    }

    public static func device() -> MockEnvironment { .init(isSimulator: false) }
    public static func simulator() -> MockEnvironment { .init(isSimulator: true) }
}
