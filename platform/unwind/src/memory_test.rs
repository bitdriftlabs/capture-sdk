// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{AddressRange, MemoryReader, StackMemory};

#[test]
fn range_rejects_empty_and_overflowing_bounds() {
  assert_eq!(AddressRange::new(1, 0), None);
  assert_eq!(AddressRange::new(u64::MAX, 1), None);
}

#[test]
fn range_checks_the_entire_read_without_overflow() {
  let range = AddressRange::new(0x1000, 32).unwrap();
  assert!(range.contains(0x1000, 32));
  assert!(range.contains(0x1010, 16));
  assert!(!range.contains(0x1000, 33));
  assert!(!range.contains(0x1020, 1));
  assert!(!range.contains(0x0fff, 1));
  assert!(!range.contains(u64::MAX, 16));
  assert!(!range.contains(0x1000, u64::MAX));
}

#[test]
fn stack_copy_translates_original_addresses() {
  let memory = StackMemory {
    address: 0x2000,
    bytes: &[1, 2, 3, 4],
  };
  let mut output = [0; 2];
  assert!(memory.read(0x2001, &mut output));
  assert_eq!(output, [2, 3]);
}

#[test]
fn stack_copy_does_not_modify_output_on_invalid_range() {
  let memory = StackMemory {
    address: 0x2000,
    bytes: &[1, 2, 3, 4],
  };
  for address in [0x1fff, 0x2003, 0x2004, u64::MAX] {
    let mut output = [9; 2];
    assert!(!memory.read(address, &mut output));
    assert_eq!(output, [9; 2]);
  }
}
