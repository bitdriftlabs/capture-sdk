// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::coordinator::Coordinator;
use crate::previous::{PreviousCrashDetails, PreviousCrashState};
use std::ffi::CStr;
use std::os::raw::c_char;
use std::sync::{Mutex, OnceLock};

static COORDINATOR: OnceLock<Coordinator> = OnceLock::new();
static CONFIGURE_LOCK: Mutex<()> = Mutex::new(());

fn previous_crash_state() -> Option<&'static PreviousCrashState> {
  COORDINATOR.get().map(Coordinator::previous_crash_state)
}

fn configure_lock() -> std::sync::MutexGuard<'static, ()> {
  match CONFIGURE_LOCK.lock() {
    Ok(guard) => guard,
    Err(poisoned) => poisoned.into_inner(),
  }
}

/// # Safety
///
/// `state_path` must point to a valid, null-terminated C string for the duration of the call.
#[no_mangle]
pub unsafe extern "C" fn capture_bitdrift_crash_configure(state_path: *const c_char) -> bool {
  if state_path.is_null() {
    log::debug!("capture_bitdrift_crash_configure called with null state path");
    return false;
  }

  let _guard = configure_lock();
  if COORDINATOR.get().is_some() {
    return true;
  }

  let path = unsafe { CStr::from_ptr(state_path) };
  let coordinator = match Coordinator::new(path) {
    Ok(coordinator) => coordinator,
    Err(error) => {
      log::warn!("failed to configure bitdrift crash coordinator: {error:#}");
      return false;
    }
  };

  COORDINATOR.set(coordinator).is_ok()
}

#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_start() -> bool {
  COORDINATOR.get().is_some_and(Coordinator::start)
}

#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_stop() {
  if let Some(coordinator) = COORDINATOR.get() {
    coordinator.stop();
  }
}

#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_did_crash_last_launch() -> i8 {
  if COORDINATOR.get().is_none() {
    return -1;
  }

  i8::from(previous_crash_state().is_some_and(|state| state.did_crash))
}

#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_cached_timestamp() -> u64 {
  previous_crash_state().map_or(0, |state| state.timestamp_secs)
}

#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_cached_kind() -> u8 {
  previous_crash_state().map_or(0, |state| state.kind as u8)
}

#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_last_exception_name() -> *const c_char {
  let Some(previous_state) = previous_crash_state() else {
    return std::ptr::null();
  };

  let PreviousCrashDetails::NSException(exception) = &previous_state.details else {
    return std::ptr::null();
  };

  if exception.name[0] == 0 {
    return std::ptr::null();
  }

  exception.name.as_ptr().cast::<c_char>()
}

#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_last_exception_reason() -> *const c_char {
  let Some(previous_state) = previous_crash_state() else {
    return std::ptr::null();
  };

  let PreviousCrashDetails::NSException(exception) = &previous_state.details else {
    return std::ptr::null();
  };

  if exception.reason[0] == 0 {
    return std::ptr::null();
  }

  exception.reason.as_ptr().cast::<c_char>()
}
