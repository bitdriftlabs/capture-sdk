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
use crate::{CancellationToken, Profiler, ThreadStatus};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, mpsc};
use std::time::{Duration, Instant};

const TH_STATE_WAITING: i32 = 3;

#[test]
fn a_waiting_thread_with_user_context_still_has_a_captured_stack() {
  let _capture = crate::tests::CAPTURE_TEST_LOCK.lock().unwrap();
  let (ready_tx, ready_rx) = mpsc::channel();
  let (stop_tx, stop_rx) = mpsc::channel();
  let worker = std::thread::Builder::new()
    .name("waiting-stack-worker".into())
    .spawn(move || {
      // Transfer this additional send right to the test, then block until capture finishes.
      ready_tx.send(unsafe { mach_thread_self() }).unwrap();
      stop_rx.recv().unwrap();
    })
    .unwrap();
  let port = ready_rx.recv().unwrap();
  let deadline = Instant::now() + Duration::from_secs(5);
  let mut waiting = false;
  while Instant::now() < deadline {
    if basic_info(port).is_some_and(|info| info[6] == TH_STATE_WAITING) {
      waiting = true;
      break;
    }
    std::thread::yield_now();
  }
  let result = waiting.then(|| {
    let cancellation = CancellationToken::default();
    Profiler::new(256, &cancellation).and_then(|mut p| p.capture(&cancellation))
  });
  // Release the worker and its send right before asserting capture results.
  stop_tx.send(()).unwrap();
  worker.join().unwrap();
  unsafe {
    mach_port_deallocate(mach_task_self_, port);
  }
  assert!(
    waiting,
    "worker must enter a Mach waiting state before capture"
  );
  let snapshot = result.unwrap().unwrap();
  let captured = snapshot
    .threads
    .iter()
    .find(|t| t.name == "waiting-stack-worker")
    .unwrap();
  assert!(matches!(captured.status, ThreadStatus::Captured(_)));
  assert!(captured.frames.iter().any(|frame| frame.address != 0));
}

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
  let cancellation = Arc::new(CancellationToken::default());
  let cancel_requested = Arc::new(AtomicBool::new(false));
  let cancel_finished = Arc::new(AtomicBool::new(false));
  let cancel_token = cancellation.clone();
  let requested = cancel_requested.clone();
  let finished = cancel_finished.clone();
  let canceller = std::thread::spawn(move || {
    while !requested.load(Ordering::Acquire) {
      std::hint::spin_loop();
    }
    cancel_token.cancel();
    finished.store(true, Ordering::Release);
  });
  // Establish one suspension owned by the test, then add and drop the collector's suspension.
  let initial = unsafe { thread_suspend(port) };
  let guard = Suspended::new(port);
  let before = suspend_count(port);
  cancel_requested.store(true, Ordering::Release);
  while !cancel_finished.load(Ordering::Acquire) {
    std::hint::spin_loop();
  }
  let after_cancel = suspend_count(port);
  drop(guard);
  let after = suspend_count(port);
  // Resume before asserting or touching allocator-backed values.
  let resumed = unsafe { super::thread_resume(port) };
  running.store(false, Ordering::Relaxed);
  worker.join().unwrap();
  canceller.join().unwrap();
  unsafe {
    mach_port_deallocate(mach_task_self_, port);
  }
  assert_eq!(initial, 0);
  assert_eq!(resumed, 0);
  assert!(cancellation.is_cancelled());
  assert_eq!(before, Some(2));
  assert_eq!(after_cancel, Some(2));
  assert_eq!(after, Some(1));
}

fn suspend_count(port: u32) -> Option<i32> {
  basic_info(port).map(|info| info[8])
}

fn basic_info(port: u32) -> Option<[i32; THREAD_BASIC_INFO_COUNT as usize]> {
  let mut info = [0_i32; THREAD_BASIC_INFO_COUNT as usize];
  let mut count = THREAD_BASIC_INFO_COUNT;
  let result = unsafe { thread_info(port, THREAD_BASIC_INFO, info.as_mut_ptr(), &raw mut count) };
  (result == 0 && count == THREAD_BASIC_INFO_COUNT).then_some(info)
}
