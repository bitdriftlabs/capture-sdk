// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::{Collector, MAX_STACK_COPY_BYTES, ThreadCapture, filled_buffer};
use crate::mach::{Threads, mach_task_self_, registers};
use crate::{CancellationToken, CaptureError, ThreadStatus};
use bd_unwind::aarch64::{Frame, FrameKind, Registers};

#[link(name = "System")]
unsafe extern "C" {
  fn thread_create(task: u32, thread: *mut u32) -> i32;
  fn thread_terminate(thread: u32) -> i32;
  fn mach_port_deallocate(task: u32, name: u32) -> i32;
}

//
// UnstartedThread
//

// An unstarted Mach thread owns no allocator/runtime locks and must never be resumed into user
// code. Termination releases its initial suspension; the collector balances only its own.
struct UnstartedThread(u32);

impl Drop for UnstartedThread {
  fn drop(&mut self) {
    // SAFETY: this fixture owns a live thread and the send right returned by thread_create.
    unsafe {
      thread_terminate(self.0);
      mach_port_deallocate(mach_task_self_, self.0);
    }
  }
}

#[test]
fn captures_an_unstarted_mach_thread_without_fabricating_a_zero_pc() {
  let _capture = crate::tests::CAPTURE_TEST_LOCK.lock().unwrap();
  let mut port = 0;
  // SAFETY: output is writable and the fixture owns the returned suspended thread/send right.
  let status = unsafe { thread_create(mach_task_self_, &raw mut port) };
  assert_eq!(status, 0);
  let thread = UnstartedThread(port);
  let state = registers(thread.0).unwrap();
  assert_eq!((state.pc, state.sp), (0, 0));
  let threads = Threads::new().unwrap();
  let mut collector = Collector::new(4096, threads.current).unwrap();
  let mut frames = [Frame::default(); 8];
  let result = collector
    .capture_thread(thread.0, &CancellationToken::default(), &mut frames)
    .unwrap();
  assert_eq!(result.status, ThreadStatus::NoUserContext);
  assert_eq!(result.frame_count, 0);
  assert_eq!(result.copied_stack_bytes, 0);
}

#[test]
fn missing_stack_classification_requires_both_pc_and_sp_to_be_zero() {
  for (pc, sp, expected, frame_count) in [
    (0, 0, ThreadStatus::NoUserContext, 0),
    (0x1000, 0, ThreadStatus::StackUnavailable, 1),
    (0, 0x2000, ThreadStatus::StackUnavailable, 0),
    (0x1000, 0x2000, ThreadStatus::StackUnavailable, 1),
  ] {
    let state = Registers {
      pc,
      sp,
      ..Registers::default()
    };
    let mut frames = [Frame::default(); 1];
    let mut result = ThreadCapture::skipped();
    result.set_unavailable(Some(&state), &mut frames);
    assert_eq!(result.status, expected);
    assert_eq!(result.frame_count, frame_count);
    if frame_count != 0 {
      assert_eq!(
        frames[0],
        Frame {
          address: pc,
          kind: FrameKind::ProgramCounter
        }
      );
    }
  }
  let mut result = ThreadCapture::skipped();
  result.set_unavailable(None, &mut [Frame::default(); 1]);
  assert_eq!(result.status, ThreadStatus::RegistersUnavailable);
  assert_eq!(result.frame_count, 0);
}

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

#[test]
fn frame_scratch_can_be_smaller_than_the_thread_count() {
  let _capture = crate::tests::CAPTURE_TEST_LOCK.lock().unwrap();
  let mut port = 0;
  // Use a stable thread owned by this fixture: unrelated harness threads may exit during capture.
  assert_eq!(unsafe { thread_create(mach_task_self_, &raw mut port) }, 0);
  let thread = UnstartedThread(port);
  let threads = Threads::new().unwrap();
  let ports = [threads.current, thread.0];
  let mut collector = Collector::new(4096, threads.current).unwrap();
  let mut frames = [Frame::default(); 1];
  let mut observed = 0;
  collector
    .sample_threads(
      &ports,
      threads.current,
      &CancellationToken::default(),
      &mut frames,
      |_| observed += 1,
    )
    .unwrap();
  assert_eq!(observed, ports.len());
}
