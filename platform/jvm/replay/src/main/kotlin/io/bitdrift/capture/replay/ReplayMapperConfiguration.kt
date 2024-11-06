// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

/**
 * Map used to matching third party Android views to Bitdrift view types
 * @param customMapper list of known types
 */
class ReplayMapperConfiguration(
    val customMapper: Map<ReplayType, List<String>>,
)
