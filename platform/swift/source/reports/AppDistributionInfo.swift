// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

internal import CaptureLoggerBridge

struct AppDistributionInfo: Equatable {
    let environment: AppEnvironment
    let teamIdentifier: String?
}
