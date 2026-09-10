// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// The previous-run confidence passed to the native logger before its startup replay begins.
/// Values mirror `bd_logger::StartupReplayEligibility`.
enum StartupReplayEligibility: Int32 {
    case noPriorCrash = 0
    case mayHavePriorCrash = 1
    case unknown = 2
}
