// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;
use std::rc::Rc;

#[test]
fn push_stops_at_capacity() {
  let mut vec = MappedVec::with_capacity(2).unwrap();
  assert_eq!(vec.push(1u64), Ok(()));
  assert_eq!(vec.push(2), Ok(()));
  assert_eq!(vec.push(3), Err(3));
  assert_eq!(&vec[..], &[1, 2]);
  assert_eq!(vec.capacity(), 2);
}

#[test]
fn zeroed_starts_full_of_zeros() {
  // SAFETY: zero is a valid u64.
  let vec = unsafe { MappedVec::<u64>::zeroed(100_000) }.unwrap();
  assert_eq!(vec.len(), 100_000);
  assert!(vec.iter().all(|value| *value == 0));
}

#[test]
fn elements_are_dropped_on_clear_and_on_drop() {
  let value = Rc::new(());
  let mut vec = MappedVec::with_capacity(4).unwrap();
  vec.push(Rc::clone(&value)).unwrap();
  vec.clear();
  assert_eq!(Rc::strong_count(&value), 1);
  vec.push(Rc::clone(&value)).unwrap();
  drop(vec);
  assert_eq!(Rc::strong_count(&value), 1);
}

#[test]
fn zero_capacity_maps_nothing() {
  let mut vec = MappedVec::<u64>::with_capacity(0).unwrap();
  assert!(vec.is_empty());
  assert_eq!(vec.push(1), Err(1));
}

#[test]
fn dropping_returns_the_memory() {
  let _lock = crate::test_support::memory_lock();
  let footprint = || {
    crate::sys::task_info::task_vm_info()
      .unwrap()
      .info
      .phys_footprint
  };
  let size = 32 << 20;
  let before = footprint();
  let mut vec = MappedVec::<u8>::with_capacity(size).unwrap();
  for _ in 0 .. size {
    vec.push(1).unwrap();
  }
  assert!(footprint() >= before + (size as u64) / 2);
  drop(vec);
  assert!(footprint() < before + (size as u64) / 4);
}
