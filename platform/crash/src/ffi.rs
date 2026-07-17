// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

//! C ABI entrypoints for configuring crash reporting, toggling monitor installation, and reading
//! cached previous-launch crash state from non-Rust callers.

#[cfg(test)]
#[path = "./ffi_test.rs"]
mod tests;

use crate::coordinator::Coordinator;
use crate::previous::{NSExceptionCrashInfo, PreviousCrashDetails, PreviousCrashState};
use std::ffi::CStr;
use std::os::raw::c_char;
use std::ptr::null;
use std::sync::{Mutex, MutexGuard, OnceLock};

static COORDINATOR: OnceLock<Coordinator> = OnceLock::new();
static CONFIGURE_LOCK: Mutex<()> = Mutex::new(());

fn previous_crash_state() -> Option<&'static PreviousCrashState> {
  COORDINATOR.get().map(Coordinator::previous_crash_state)
}

fn previous_nsexception(previous_state: &PreviousCrashState) -> Option<&NSExceptionCrashInfo> {
  let PreviousCrashDetails::NSException(exception) = &previous_state.details else {
    return None;
  };

  Some(exception.as_ref())
}

fn configure_lock() -> MutexGuard<'static, ()> {
  match CONFIGURE_LOCK.lock() {
    Ok(guard) => guard,
    Err(poisoned) => poisoned.into_inner(),
  }
}

/// # Safety
///
/// `state_path` must point to a valid, null-terminated C string for the duration of the call.
///
/// This function is idempotent for the process: it initializes the persisted crash state store,
/// snapshots the previous launch's crash state, and prepares the current run's shared record, but
/// it does not install crash monitors yet.
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

  // Serialize first-time initialization so a second caller cannot construct and immediately drop a
  // new coordinator while the shared mmap-backed crash record still points into the original one.
  let path = unsafe { CStr::from_ptr(state_path) };
  let coordinator = match Coordinator::new(path) {
    Ok(coordinator) => coordinator,
    Err(error) => {
      log::warn!("failed to configure bitdrift crash coordinator: {error:#}");
      return false;
    },
  };

  COORDINATOR.set(coordinator).is_ok()
}

/// Activate crash monitor installation for the current process. Returns `false` if the
/// coordinator has not been configured yet or monitor installation fails.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_start() -> bool {
  COORDINATOR.get().is_some_and(Coordinator::start)
}

/// Uninstall previously-registered crash monitors for the current process. This does not destroy
/// the coordinator or clear persisted crash state.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_stop() {
  if let Some(coordinator) = COORDINATOR.get() {
    coordinator.stop();
  }
}

/// Return whether the cached previous-launch state indicates a crash. Returns `-1` when the
/// coordinator has not been configured yet.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_did_crash_last_launch() -> i8 {
  if COORDINATOR.get().is_none() {
    return -1;
  }

  i8::from(previous_crash_state().is_some_and(|state| state.did_crash))
}

/// Return the cached previous-launch crash timestamp, or `0` when no crash state is available.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_cached_timestamp() -> u64 {
  previous_crash_state().map_or(0, |state| state.timestamp_secs)
}

/// Return the cached previous-launch crash kind, or `0` when no crash state is available.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_cached_kind() -> u8 {
  previous_crash_state().map_or(0, |state| state.kind as u8)
}

/// Return the cached previous-launch exception name as a pointer into process-owned storage, or
/// null when no `NSException` name is available.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_last_exception_name() -> *const c_char {
  let Some(previous_state) = previous_crash_state() else {
    return null();
  };

  let Some(exception) = previous_nsexception(previous_state) else {
    return null();
  };

  if exception.name[0] == 0 {
    return null();
  }

  exception.name.as_ptr().cast::<c_char>()
}

/// Return the cached previous-launch exception reason as a pointer into process-owned storage, or
/// null when no `NSException` reason is available.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_last_exception_reason() -> *const c_char {
  let Some(previous_state) = previous_crash_state() else {
    return null();
  };

  let Some(exception) = previous_nsexception(previous_state) else {
    return null();
  };

  if exception.reason[0] == 0 {
    return null();
  }

  exception.reason.as_ptr().cast::<c_char>()
}

/// Return the number of cached previous-launch `NSException` call stack frames, or `0` when no
/// `NSException` call stack is available.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_last_exception_call_stack_frame_count() -> u16 {
  let Some(previous_state) = previous_crash_state() else {
    return 0;
  };

  let Some(exception) = previous_nsexception(previous_state) else {
    return 0;
  };

  exception.call_stack.frame_count
}

/// Return a pointer to cached previous-launch `NSException` return addresses, or null when no
/// `NSException` call stack is available.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_last_exception_call_stack_return_addresses() -> *const u64
{
  let Some(previous_state) = previous_crash_state() else {
    return null();
  };

  let Some(exception) = previous_nsexception(previous_state) else {
    return null();
  };

  if exception.call_stack.frame_count == 0 {
    return null();
  }

  exception.call_stack.return_addresses.as_ptr()
}

/// Return the cached previous-launch `NSException` image load address for the requested frame, or
/// `0` when the frame is unavailable.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_last_exception_call_stack_image_load_address_at(
  frame_index: u16,
) -> u64 {
  let Some(previous_state) = previous_crash_state() else {
    return 0;
  };

  let Some(exception) = previous_nsexception(previous_state) else {
    return 0;
  };

  let index = usize::from(frame_index);
  if index >= usize::from(exception.call_stack.frame_count) {
    return 0;
  }

  exception.call_stack.frames[index].image_load_address
}

/// Return the cached previous-launch `NSException` binary name for the requested frame, or null
/// when the frame is unavailable or has no binary name.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_last_exception_call_stack_binary_name_at(
  frame_index: u16,
) -> *const c_char {
  let Some(previous_state) = previous_crash_state() else {
    return null();
  };

  let Some(exception) = previous_nsexception(previous_state) else {
    return null();
  };

  let index = usize::from(frame_index);
  if index >= usize::from(exception.call_stack.frame_count) {
    return null();
  }

  let frame = &exception.call_stack.frames[index];
  if frame.binary_name[0] == 0 {
    return null();
  }

  frame.binary_name.as_ptr().cast::<c_char>()
}

/// Return the cached previous-launch `NSException` image id for the requested frame, or null when
/// the frame is unavailable or has no image id.
#[no_mangle]
pub extern "C" fn capture_bitdrift_crash_last_exception_call_stack_image_id_at(
  frame_index: u16,
) -> *const c_char {
  let Some(previous_state) = previous_crash_state() else {
    return null();
  };

  let Some(exception) = previous_nsexception(previous_state) else {
    return null();
  };

  let index = usize::from(frame_index);
  if index >= usize::from(exception.call_stack.frame_count) {
    return null();
  }

  let frame = &exception.call_stack.frames[index];
  if frame.image_id[0] == 0 {
    return null();
  }

  frame.image_id.as_ptr().cast::<c_char>()
}
