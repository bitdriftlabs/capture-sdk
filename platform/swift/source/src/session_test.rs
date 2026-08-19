// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::{MAX_DURATION_SECONDS_EXCLUSIVE, timeout_from_seconds};
use time::Duration;

#[test]
fn timeout_from_seconds_handles_absent_and_valid_values() {
  assert!(matches!(timeout_from_seconds(-1.0), Ok(None)));
  assert!(matches!(
    timeout_from_seconds(0.5),
    Ok(Some(duration)) if duration == Duration::milliseconds(500)
  ));
}

#[test]
fn timeout_from_seconds_rejects_non_finite_and_unrepresentable_values() {
  for seconds in [
    f64::NAN,
    f64::INFINITY,
    f64::NEG_INFINITY,
    MAX_DURATION_SECONDS_EXCLUSIVE,
  ] {
    assert!(timeout_from_seconds(seconds).is_err());
  }
}
