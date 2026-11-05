// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#[cfg(test)]
#[path = "./stack_snapshot_test.rs"]
mod tests;

use crate::stack_snapshot_output::{ExportError, write_attachment};
use profiler::{CancellationToken, CaptureError, Profiler, Summary};
use std::ffi::{CStr, c_char, c_void};
use std::path::Path;

// This SDK ceiling bounds the encoded file, independently of the capture buffer. The core still
// enforces its runtime-configured upload limit, including configurations below this ceiling.
const MAX_SNAPSHOT_JSON_BYTES: usize = 5 * 1024 * 1024;

#[unsafe(no_mangle)]
pub extern "C" fn capture_stack_snapshot_bridge_create() -> *mut c_void {
  Box::into_raw(Box::new(CancellationToken::default())).cast()
}

/// # Safety
/// `bridge` must be a live handle returned by `capture_stack_snapshot_bridge_create`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn capture_stack_snapshot_bridge_cancel(bridge: *const c_void) {
  // SAFETY: the owner retains the bridge until cancellation and capture have both finished.
  if let Some(bridge) = unsafe { bridge.cast::<CancellationToken>().as_ref() } {
    bridge.cancel();
  }
}

/// # Safety
/// `bridge` must be an owned handle from create, with no remaining capture or cancellation calls.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn capture_stack_snapshot_bridge_release(bridge: *mut c_void) {
  if !bridge.is_null() {
    // SAFETY: ownership of this Box is transferred back exactly once by the Swift owner.
    drop(unsafe { Box::from_raw(bridge.cast::<CancellationToken>()) });
  }
}

/// Captures stacks and creates a JSON attachment at an exclusively owned path.
///
/// # Safety
/// `path` must be a NUL-terminated UTF-8 string. `bridge` must remain live for this call;
/// cancellation may set its atomic flag concurrently. `summary` must point to writable storage.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn capture_stack_snapshot(
  buffer_size_kb: u32,
  path: *const c_char,
  bridge: *const c_void,
  summary: *mut Summary,
) -> i32 {
  if path.is_null() || summary.is_null() {
    return 1;
  }
  // SAFETY: the caller supplies exclusive writable output storage and a live cancellation handle.
  unsafe {
    summary.write(Summary::default());
  }
  let Some(bridge) = (unsafe { bridge.cast::<CancellationToken>().as_ref() }) else {
    return 1;
  };
  // SAFETY: caller owns the string until this synchronous operation returns.
  let Ok(path) = unsafe { CStr::from_ptr(path) }.to_str() else {
    return 1;
  };
  // Retain the process-wide slot through export, including cancellation and file cleanup.
  let result = Profiler::new(buffer_size_kb, bridge);
  let mut profiler = match result {
    Ok(profiler) => profiler,
    Err(error) => return capture_error(error),
  };
  let snapshot = match profiler.capture(bridge) {
    Ok(snapshot) => snapshot,
    Err(error) => return capture_error(error),
  };
  if let Err(error) = write_attachment(&snapshot, Path::new(path), MAX_SNAPSHOT_JSON_BYTES, bridge)
  {
    return match error {
      ExportError::Io => 5,
      ExportError::TooLarge => 6,
      ExportError::Cancelled => 7,
    };
  }
  // SAFETY: output storage remains exclusively owned by this synchronous invocation.
  unsafe {
    summary.write(snapshot.summary());
  }
  log::debug!(
    "stack snapshot completed: {} threads",
    snapshot.threads.len()
  );
  0
}

const fn capture_error(error: CaptureError) -> i32 {
  match error {
    CaptureError::InvalidBufferSize => 1,
    CaptureError::AlreadyCapturing => 2,
    CaptureError::UnsupportedArchitecture => 3,
    CaptureError::ThreadEnumerationFailed | CaptureError::ResumeFailed => 4,
    CaptureError::Cancelled => 7,
    CaptureError::NoUsefulStacks => 8,
  }
}
