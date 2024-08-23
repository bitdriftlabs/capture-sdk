// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// A protocol representing a provider that can take a snapshot of a resource.
protocol ResourceSnapshotProvider {
    /// Captures resource snapshot.
    ///
    /// - returns: Captured resource snapshot.
    func makeSnapshot() throws -> ResourceSnapshot?
}
