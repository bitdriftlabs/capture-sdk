// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{
  chain_previous,
  handle_exception_snapshot,
  previous_handler,
  store_previous_handler,
  try_enter_handler,
  ExceptionHandler,
  ExceptionSnapshot,
  IN_HANDLER,
  PREVIOUS_HANDLER,
};
use crate::schema::{self, CrashKind, CrashRecord, RecordState};
use crate::test_support::test_crash_record_guard;
use crate::writer::{prime_shared_record, CRASH_RECORD};
use objc2_foundation::NSException;
use std::ptr::{null_mut, NonNull};
use std::sync::atomic::{AtomicPtr, AtomicUsize, Ordering};
use std::sync::{Mutex, MutexGuard};

static TEST_MONITOR_STATE_LOCK: Mutex<()> = Mutex::new(());
static PREVIOUS_CALL_COUNT: AtomicUsize = AtomicUsize::new(0);
static PREVIOUS_LAST_EXCEPTION: AtomicPtr<NSException> = AtomicPtr::new(null_mut());

struct TestMonitorStateGuard {
  _guard: MutexGuard<'static, ()>,
}

impl Drop for TestMonitorStateGuard {
  fn drop(&mut self) {
    IN_HANDLER.store(false, Ordering::SeqCst);
    PREVIOUS_HANDLER.store(0, Ordering::Release);
    PREVIOUS_CALL_COUNT.store(0, Ordering::Release);
    PREVIOUS_LAST_EXCEPTION.store(null_mut(), Ordering::Release);
  }
}

fn test_monitor_state_guard() -> TestMonitorStateGuard {
  let guard = match TEST_MONITOR_STATE_LOCK.lock() {
    Ok(guard) => guard,
    Err(poisoned) => poisoned.into_inner(),
  };
  IN_HANDLER.store(false, Ordering::SeqCst);
  PREVIOUS_HANDLER.store(0, Ordering::Release);
  PREVIOUS_CALL_COUNT.store(0, Ordering::Release);
  PREVIOUS_LAST_EXCEPTION.store(null_mut(), Ordering::Release);
  TestMonitorStateGuard { _guard: guard }
}

unsafe extern "C" fn fake_previous_handler(exception: *mut NSException) {
  PREVIOUS_CALL_COUNT.fetch_add(1, Ordering::AcqRel);
  PREVIOUS_LAST_EXCEPTION.store(exception, Ordering::Release);
}

fn exception_snapshot() -> ExceptionSnapshot {
  ExceptionSnapshot {
    name: "NSException".to_string(),
    reason: Some("bad reason".to_string()),
    frames: vec![
      super::ExceptionFrameSnapshot {
        return_address: 0x1234,
        image_load_address: 0x1000,
        binary_name: Some("MyApp".to_string()),
        image_id: Some("BD9C11B4-BF87-3F60-AEA0-0141BD7F8AC0".to_string()),
      },
      super::ExceptionFrameSnapshot {
        return_address: 0x5678,
        image_load_address: 0,
        binary_name: None,
        image_id: None,
      },
    ],
  }
}

fn current_record() -> &'static CrashRecord {
  let record_ptr = CRASH_RECORD.load(Ordering::Acquire);
  unsafe { &*record_ptr }
}

#[test]
fn try_enter_handler_rejects_reentrant_entry() {
  let _guard = test_monitor_state_guard();

  assert!(try_enter_handler());
  assert!(!try_enter_handler());
}

#[test]
fn previous_handler_round_trips_none_and_some() {
  let _guard = test_monitor_state_guard();

  store_previous_handler(None);
  assert!(previous_handler().is_none());

  let handler: ExceptionHandler = fake_previous_handler;
  store_previous_handler(Some(handler));
  assert!(previous_handler().is_some());
}

#[test]
fn chain_previous_calls_registered_handler_with_same_exception_pointer() {
  let _guard = test_monitor_state_guard();
  let exception = NonNull::<NSException>::dangling().as_ptr();
  store_previous_handler(Some(fake_previous_handler));

  chain_previous(exception);

  assert_eq!(PREVIOUS_CALL_COUNT.load(Ordering::Acquire), 1);
  assert_eq!(PREVIOUS_LAST_EXCEPTION.load(Ordering::Acquire), exception);
}

#[test]
fn handle_exception_snapshot_records_snapshot_and_chains_previous() {
  let _monitor_guard = test_monitor_state_guard();
  let _record_guard = test_crash_record_guard();
  let mut record = CrashRecord::default();
  unsafe {
    prime_shared_record(&raw mut record);
  }
  let exception = NonNull::<NSException>::dangling().as_ptr();
  store_previous_handler(Some(fake_previous_handler));

  handle_exception_snapshot(exception, Some(exception_snapshot()));

  let record = current_record();
  assert_eq!(record.header.record_state, RecordState::Committed);
  assert_eq!(record.header.crash_kind, CrashKind::NSException);
  assert_eq!(&record.nsexception.name[.. 12], b"NSException\0");
  assert_eq!(&record.nsexception.reason[.. 11], b"bad reason\0");
  assert_eq!(record.nsexception.call_stack.frame_count, 2);
  assert_eq!(
    record.nsexception.call_stack.frames[0].return_address,
    0x1234
  );
  assert_eq!(
    record.nsexception.call_stack.frames[1].return_address,
    0x5678
  );
  assert_eq!(
    record.nsexception.call_stack.frames[0].image_load_address,
    0x1000
  );
  assert_eq!(
    &record.nsexception.call_stack.frames[0].binary_name[.. 6],
    b"MyApp\0"
  );
  assert_eq!(PREVIOUS_CALL_COUNT.load(Ordering::Acquire), 1);
  assert_eq!(PREVIOUS_LAST_EXCEPTION.load(Ordering::Acquire), exception);
}

#[test]
fn handle_exception_snapshot_chains_null_exception_without_recording() {
  let _monitor_guard = test_monitor_state_guard();
  let _record_guard = test_crash_record_guard();
  let mut record = CrashRecord::default();
  unsafe {
    prime_shared_record(&raw mut record);
  }
  store_previous_handler(Some(fake_previous_handler));

  handle_exception_snapshot(null_mut(), None);

  let record = current_record();
  assert_eq!(record.header.record_state, RecordState::Empty);
  assert_eq!(record.header.crash_kind, CrashKind::None);
  assert_eq!(PREVIOUS_CALL_COUNT.load(Ordering::Acquire), 1);
  assert!(PREVIOUS_LAST_EXCEPTION.load(Ordering::Acquire).is_null());
}

#[test]
fn handle_exception_snapshot_ignores_reentrant_invocation() {
  let _monitor_guard = test_monitor_state_guard();
  let _record_guard = test_crash_record_guard();
  let mut record = CrashRecord::default();
  unsafe {
    prime_shared_record(&raw mut record);
  }
  store_previous_handler(Some(fake_previous_handler));
  assert!(try_enter_handler());

  handle_exception_snapshot(
    NonNull::<NSException>::dangling().as_ptr(),
    Some(exception_snapshot()),
  );

  let record = current_record();
  assert_eq!(record.header.record_state, RecordState::Empty);
  assert_eq!(record.header.crash_kind, CrashKind::None);
  assert_eq!(PREVIOUS_CALL_COUNT.load(Ordering::Acquire), 0);
}

#[test]
fn handle_exception_snapshot_records_missing_reason_and_empty_stack() {
  let _monitor_guard = test_monitor_state_guard();
  let _record_guard = test_crash_record_guard();
  let mut record = CrashRecord::default();
  unsafe {
    prime_shared_record(&raw mut record);
  }

  handle_exception_snapshot(
    null_mut(),
    Some(ExceptionSnapshot {
      name: "NSException".to_string(),
      reason: None,
      frames: Vec::new(),
    }),
  );

  let record = current_record();
  assert_eq!(record.header.record_state, RecordState::Committed);
  assert_eq!(record.nsexception.reason[0], 0);
  assert_eq!(record.nsexception.call_stack.frame_count, 0);
  assert!(record
    .nsexception
    .call_stack
    .frames
    .iter()
    .all(|frame| frame == &schema::RawNSExceptionStackFrame::default()));
}
