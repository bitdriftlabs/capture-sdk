// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./nsexception_test.rs"]
mod tests;

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

//
// NSExceptionMonitor
//

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
  // The system invokes the uncaught exception handler at the point of termination, so keep the
  // critical section small and reject re-entrant handler entry.
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
  // NSGetUncaughtExceptionHandler returns a raw function pointer. Store the previous handler so it
  // can be restored on uninstall and chained after the current snapshot is recorded.
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
