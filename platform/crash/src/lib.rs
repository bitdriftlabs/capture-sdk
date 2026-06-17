// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![deny(
  clippy::expect_used,
  clippy::panic,
  clippy::todo,
  clippy::unimplemented,
  clippy::unreachable,
  clippy::unwrap_used
)]

mod coordinator;
mod monitors;
mod state;
mod store;

use coordinator::Coordinator;
use state::{PreviousCrashDetails, PreviousCrashState};
use std::ffi::CStr;
use std::os::raw::c_char;
use std::sync::OnceLock;

static COORDINATOR: OnceLock<Coordinator> = OnceLock::new();

fn previous_crash_state() -> Option<&'static PreviousCrashState> {
  COORDINATOR.get().map(Coordinator::previous_crash_state)
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn capture_bitdrift_crash_configure(state_path: *const c_char) -> bool {
  if state_path.is_null() {
    return false;
  }

  let path = unsafe { CStr::from_ptr(state_path) };
  let Ok(coordinator) = Coordinator::new(path) else {
    return false;
  };

  COORDINATOR.set(coordinator).is_ok()
}

#[unsafe(no_mangle)]
pub extern "C" fn capture_bitdrift_crash_start() -> bool {
  COORDINATOR.get().is_some_and(Coordinator::start)
}

#[unsafe(no_mangle)]
pub extern "C" fn capture_bitdrift_crash_stop() {
  if let Some(coordinator) = COORDINATOR.get() {
    coordinator.stop();
  }
}

#[unsafe(no_mangle)]
pub extern "C" fn capture_bitdrift_crash_did_crash_last_launch() -> i8 {
  if COORDINATOR.get().is_none() {
    return -1;
  }

  i8::from(previous_crash_state().is_some_and(|state| state.did_crash))
}

#[unsafe(no_mangle)]
pub extern "C" fn capture_bitdrift_crash_cached_timestamp() -> u64 {
  previous_crash_state().map_or(0, |state| state.timestamp_secs)
}

#[unsafe(no_mangle)]
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

#[unsafe(no_mangle)]
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
