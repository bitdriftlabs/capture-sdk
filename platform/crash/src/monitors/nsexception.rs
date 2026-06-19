// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::monitors::Monitor;
use crate::writer;
use objc2_foundation::{NSArray, NSException, NSNumber};
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};

type ExceptionHandler = unsafe extern "C" fn(*mut NSException);

static IN_HANDLER: AtomicBool = AtomicBool::new(false);
static PREVIOUS_HANDLER: AtomicUsize = AtomicUsize::new(0);

#[derive(Clone, Debug, PartialEq, Eq)]
struct ExceptionSnapshot {
  name: String,
  reason: Option<String>,
  return_addresses: Vec<u64>,
}

unsafe extern "C" {
  fn NSGetUncaughtExceptionHandler() -> Option<ExceptionHandler>;
  fn NSSetUncaughtExceptionHandler(handler: Option<ExceptionHandler>);
}

pub(crate) struct NSExceptionMonitor;

impl Monitor for NSExceptionMonitor {
  fn install(&self) -> bool {
    unsafe {
      let previous = NSGetUncaughtExceptionHandler();
      store_previous_handler(previous);
      NSSetUncaughtExceptionHandler(Some(handle_exception));
    }
    true
  }

  fn uninstall(&self) {
    IN_HANDLER.store(false, Ordering::SeqCst);
    let previous = previous_handler();

    unsafe {
      NSSetUncaughtExceptionHandler(previous);
    }

    PREVIOUS_HANDLER.store(0, Ordering::Release);
  }
}

unsafe extern "C" fn handle_exception(exception: *mut NSException) {
  let snapshot = unsafe { exception.as_ref() }.map(extract_exception_snapshot);
  handle_exception_snapshot(exception, snapshot);
}

fn handle_exception_snapshot(exception: *mut NSException, snapshot: Option<ExceptionSnapshot>) {
  if !try_enter_handler() {
    return;
  }

  if let Some(snapshot) = snapshot {
    record_exception_snapshot(&snapshot);
  }

  chain_previous(exception);
}

fn extract_exception_snapshot(exception: &NSException) -> ExceptionSnapshot {
  let return_addresses = exception.callStackReturnAddresses();
  let return_addresses: &NSArray<NSNumber> = return_addresses.as_ref();
  let return_addresses = (0 .. return_addresses.count())
    .map(|index| return_addresses.objectAtIndex(index).as_u64())
    .collect::<Vec<_>>();

  ExceptionSnapshot {
    name: exception.name().to_string(),
    reason: exception.reason().map(|reason| reason.to_string()),
    return_addresses,
  }
}

fn record_exception_snapshot(snapshot: &ExceptionSnapshot) {
  writer::record_nsexception(
    snapshot.name.as_str(),
    snapshot.reason.as_deref(),
    &snapshot.return_addresses,
  );
}

fn try_enter_handler() -> bool {
  !IN_HANDLER.swap(true, Ordering::SeqCst)
}

fn chain_previous(exception: *mut NSException) {
  if let Some(previous) = previous_handler() {
    unsafe {
      previous(exception);
    }
  }
}

fn store_previous_handler(handler: Option<ExceptionHandler>) {
  PREVIOUS_HANDLER.store(
    handler.map_or(0, |handler| handler as usize),
    Ordering::Release,
  );
}

fn previous_handler() -> Option<ExceptionHandler> {
  let previous = PREVIOUS_HANDLER.load(Ordering::Acquire);
  if previous == 0 {
    None
  } else {
    Some(unsafe { std::mem::transmute::<usize, ExceptionHandler>(previous) })
  }
}

#[cfg(test)]
mod tests {
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
  use crate::writer::{prime_shared_record, test_crash_record_guard, CRASH_RECORD};
  use objc2_foundation::NSException;
  use std::ptr::NonNull;
  use std::sync::atomic::{AtomicPtr, AtomicUsize, Ordering};

  static TEST_MONITOR_STATE_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());
  static PREVIOUS_CALL_COUNT: AtomicUsize = AtomicUsize::new(0);
  static PREVIOUS_LAST_EXCEPTION: AtomicPtr<NSException> = AtomicPtr::new(std::ptr::null_mut());

  struct TestMonitorStateGuard {
    _guard: std::sync::MutexGuard<'static, ()>,
  }

  impl Drop for TestMonitorStateGuard {
    fn drop(&mut self) {
      IN_HANDLER.store(false, Ordering::SeqCst);
      PREVIOUS_HANDLER.store(0, Ordering::Release);
      PREVIOUS_CALL_COUNT.store(0, Ordering::Release);
      PREVIOUS_LAST_EXCEPTION.store(std::ptr::null_mut(), Ordering::Release);
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
    PREVIOUS_LAST_EXCEPTION.store(std::ptr::null_mut(), Ordering::Release);
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
      return_addresses: vec![0x1234, 0x5678],
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
      &record.nsexception.call_stack.return_addresses[.. 2],
      &[0x1234, 0x5678]
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

    handle_exception_snapshot(std::ptr::null_mut(), None);

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
      std::ptr::null_mut(),
      Some(ExceptionSnapshot {
        name: "NSException".to_string(),
        reason: None,
        return_addresses: Vec::new(),
      }),
    );

    let record = current_record();
    assert_eq!(record.header.record_state, RecordState::Committed);
    assert_eq!(record.nsexception.reason[0], 0);
    assert_eq!(record.nsexception.call_stack.frame_count, 0);
    assert_eq!(
      record.nsexception.call_stack.return_addresses,
      [0; schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES]
    );
  }
}
