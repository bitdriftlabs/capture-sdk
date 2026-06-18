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

unsafe extern "C" {
  fn NSGetUncaughtExceptionHandler() -> Option<ExceptionHandler>;
  fn NSSetUncaughtExceptionHandler(handler: Option<ExceptionHandler>);
}

pub(crate) struct NSExceptionMonitor;

impl Monitor for NSExceptionMonitor {
  fn install(&self) -> bool {
    unsafe {
      let previous = NSGetUncaughtExceptionHandler();
      PREVIOUS_HANDLER.store(
        previous.map_or(0, |handler| handler as usize),
        Ordering::Release,
      );
      NSSetUncaughtExceptionHandler(Some(handle_exception));
    }
    true
  }

  fn uninstall(&self) {
    IN_HANDLER.store(false, Ordering::SeqCst);
    let previous = PREVIOUS_HANDLER.load(Ordering::Acquire);
    let previous = if previous == 0 {
      None
    } else {
      Some(unsafe { std::mem::transmute::<usize, ExceptionHandler>(previous) })
    };

    unsafe {
      NSSetUncaughtExceptionHandler(previous);
    }

    PREVIOUS_HANDLER.store(0, Ordering::Release);
  }
}

unsafe extern "C" fn handle_exception(exception: *mut NSException) {
  if IN_HANDLER.swap(true, Ordering::SeqCst) {
    return;
  }

  if let Some(exception) = unsafe { exception.as_ref() } {
    let name = exception.name().to_string();
    let reason = exception.reason().map(|reason| reason.to_string());
    let return_addresses = exception.callStackReturnAddresses();
    let return_addresses: &NSArray<NSNumber> = return_addresses.as_ref();
    let return_addresses = (0..return_addresses.count())
      .map(|index| return_addresses.objectAtIndex(index).as_u64())
      .collect::<Vec<_>>();
    writer::record_nsexception(name.as_str(), reason.as_deref(), &return_addresses);
  }

  let previous = PREVIOUS_HANDLER.load(Ordering::Acquire);
  if previous != 0 {
    let previous = unsafe { std::mem::transmute::<usize, ExceptionHandler>(previous) };
    unsafe {
      previous(exception);
    }
  }
}

#[cfg(test)]
mod tests {
  use crate::schema::CrashRecord;
  use crate::writer::{prime_shared_record, record_nsexception, CRASH_RECORD};
  use std::sync::atomic::Ordering;

  #[test]
  fn record_nsexception_truncates_and_null_terminates() {
    let mut state = CrashRecord::default();
    unsafe {
      prime_shared_record(&mut state);
    }

    let long_name = "a".repeat(200);
    record_nsexception(long_name.as_str(), None, &[]);

    let state = unsafe { &*CRASH_RECORD.load(Ordering::Acquire) };
    assert_eq!(state.nsexception.name[state.nsexception.name.len() - 1], 0);
  }

  #[test]
  fn record_nsexception_copies_return_addresses() {
    let mut state = CrashRecord::default();
    unsafe {
      prime_shared_record(&mut state);
    }

    record_nsexception("NSException", None, &[0x1234, 0x5678]);

    let state = unsafe { &*CRASH_RECORD.load(Ordering::Acquire) };
    assert_eq!(state.nsexception.call_stack.frame_count, 2);
    assert_eq!(
      &state.nsexception.call_stack.return_addresses[..2],
      &[0x1234, 0x5678]
    );
  }
}
