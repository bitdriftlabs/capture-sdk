// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation

public final class MockNetworkClient: NetworkClient {
    public func createConnection(to _: URL,
                                 handler _: ConnectionDataHandler,
                                 headers _: [String: String]) -> NetworkStreamConnection
    {
        return MockConnection()
    }

    public func tearDown() {}
}
