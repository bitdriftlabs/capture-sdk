// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::{
  Suspended,
  THREAD_BASIC_INFO,
  THREAD_BASIC_INFO_COUNT,
  Timebase,
  mach_port_deallocate,
  mach_task_self_,
  mach_thread_self,
  thread_info,
  thread_suspend,
};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, mpsc};

#[test]
fn failed_suspend_does_not_create_a_resume_guard() {
  assert!(Suspended::new(0).is_none());
}

#[test]
fn timebase_converts_without_intermediate_overflow() {
  let timebase = Timebase {
    numerator: 125,
    denominator: 3,
  };
  assert_eq!(timebase.nanos(3), 125);
  assert_eq!(timebase.nanos(u64::MAX), u64::MAX);
}

#[test]
fn guard_drop_balances_only_its_own_suspension() {
  let running = Arc::new(AtomicBool::new(true));
  let worker_running = running.clone();
  let (sender, receiver) = mpsc::channel();
  let worker = std::thread::spawn(move || {
    // Transfer ownership of this additional send right to the test.
    sender.send(unsafe { mach_thread_self() }).unwrap();
    while worker_running.load(Ordering::Relaxed) {
      std::hint::spin_loop();
    }
  });
  let port = receiver.recv().unwrap();
  // Establish one suspension owned by the test, then add and drop the collector's suspension.
  let initial = unsafe { thread_suspend(port) };
  let guard = Suspended::new(port);
  let before = suspend_count(port);
  drop(guard);
  let after = suspend_count(port);
  // Resume before asserting or touching allocator-backed values.
  let resumed = unsafe { super::thread_resume(port) };
  running.store(false, Ordering::Relaxed);
  worker.join().unwrap();
  unsafe {
    mach_port_deallocate(mach_task_self_, port);
  }
  assert_eq!(initial, 0);
  assert_eq!(resumed, 0);
  assert_eq!(before, Some(2));
  assert_eq!(after, Some(1));
}

fn suspend_count(port: u32) -> Option<i32> {
  let mut info = [0_i32; THREAD_BASIC_INFO_COUNT as usize];
  let mut count = THREAD_BASIC_INFO_COUNT;
  let result = unsafe { thread_info(port, THREAD_BASIC_INFO, info.as_mut_ptr(), &raw mut count) };
  (result == 0 && count == THREAD_BASIC_INFO_COUNT).then_some(info[8])
}
