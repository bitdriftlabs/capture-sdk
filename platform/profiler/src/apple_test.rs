// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::{Collector, MAX_STACK_COPY_BYTES, filled_buffer};
use crate::CaptureError;

#[test]
fn scratch_respects_small_budgets_and_caps_large_ones() {
  for budget in [1024, MAX_STACK_COPY_BYTES, usize::MAX] {
    let collector = Collector::new(budget, 0).unwrap();
    assert_eq!(collector.scratch.len(), budget.min(MAX_STACK_COPY_BYTES));
  }
}

#[test]
fn impossible_buffer_reservation_returns_a_capture_error() {
  assert_eq!(
    filled_buffer(usize::MAX, 0_u64),
    Err(CaptureError::AllocationFailed)
  );
}
