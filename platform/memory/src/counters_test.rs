// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;

#[test]
fn reports_footprint_and_resident_size() {
  let report = capture().unwrap();
  assert!(report.phys_footprint > 0);
  assert!(report.resident_size > 0);
  assert!(report.phys_footprint_peak.unwrap() >= report.phys_footprint);
}

#[test]
fn footprint_matches_the_full_capture() {
  let footprint = phys_footprint().unwrap();
  let report = capture().unwrap();
  let drift = footprint.abs_diff(report.phys_footprint);
  assert!(drift < 16 << 20, "drift of {drift} bytes");
}
