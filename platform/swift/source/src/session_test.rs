// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::timeout_from_seconds;
use time::Duration;

#[test]
fn timeout_from_seconds_handles_absent_and_valid_values() {
  assert!(timeout_from_seconds(-1.0).is_none());
  assert!(matches!(
    timeout_from_seconds(0.5),
    Some(duration) if duration == Duration::milliseconds(500)
  ));
}
