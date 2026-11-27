// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::stack_snapshot::{MAX_STACK_CAPTURE_JSON_BYTES, capture_error, main_thread_id};
use crate::stack_snapshot_output::{ExportError, write_profile_attachment};
use profiler::{CancellationToken, ProfileConfig, ProfileSummary, Profiler};
use std::ffi::{CStr, c_char, c_void};
use std::path::Path;

/// Captures and exports a periodic profile. The slot remains owned through export and cleanup.
///
/// # Safety
/// path is a NUL-terminated UTF-8 string. bridge is a live snapshot/profile cancellation handle
/// retained until this call and all cancellation calls finish. summary is writable and exclusive.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn capture_stack_profile(
  frequency_hz: u32,
  duration_ms: u32,
  buffer_size_kb: u32,
  path: *const c_char,
  bridge: *const c_void,
  summary: *mut ProfileSummary,
) -> i32 {
  if path.is_null() || summary.is_null() {
    return 1;
  }
  // SAFETY: caller supplies exclusive writable summary storage.
  unsafe {
    summary.write(ProfileSummary::default());
  }
  // SAFETY: caller retains this handle and string throughout the synchronous operation.
  let Some(cancellation) = (unsafe { bridge.cast::<CancellationToken>().as_ref() }) else {
    return 1;
  };
  let Ok(path) = unsafe { CStr::from_ptr(path) }.to_str() else {
    return 1;
  };
  let config = match ProfileConfig::new(frequency_hz, duration_ms) {
    Ok(config) => config,
    Err(error) => return capture_error(error),
  };
  let mut profiler = match Profiler::new(buffer_size_kb, cancellation) {
    Ok(profiler) => profiler,
    Err(error) => return capture_error(error),
  };
  let mut profile = match profiler.profile(config, cancellation) {
    Ok(profile) => profile,
    Err(error) => return capture_error(error),
  };
  profile.main_thread_id = main_thread_id();
  if let Err(error) = write_profile_attachment(
    &profile,
    Path::new(path),
    MAX_STACK_CAPTURE_JSON_BYTES,
    cancellation,
  ) {
    return match error {
      ExportError::Io => 5,
      ExportError::TooLarge => 6,
      ExportError::Cancelled => 7,
    };
  }
  // SAFETY: output remains exclusively owned by this call.
  unsafe {
    summary.write(profile.summary());
  }
  log::debug!(
    "stack profile completed: {} rounds, {} retained samples",
    profile.summary().capture_rounds,
    profile.summary().retained_samples
  );
  0
}
