// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use profiler::{CaptureError, capture};
use std::ffi::{CStr, c_char};
use std::fs::{self, OpenOptions};
use std::io::{BufWriter, Write};

/// Captures stacks and creates a JSON attachment at an exclusively owned path.
///
/// # Safety
/// `path` must point to a valid NUL-terminated UTF-8 string for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn capture_stack_snapshot(buffer_size_kb: u32, path: *const c_char) -> i32 {
  if path.is_null() {
    return 1;
  }
  // SAFETY: caller owns the string until this synchronous operation returns.
  let Ok(path) = unsafe { CStr::from_ptr(path) }.to_str() else {
    return 1;
  };
  let snapshot = match capture(buffer_size_kb) {
    Ok(snapshot) => snapshot,
    Err(error) => {
      return match error {
        CaptureError::InvalidBufferSize => 1,
        CaptureError::AlreadyCapturing => 2,
        CaptureError::UnsupportedArchitecture => 3,
        CaptureError::ThreadEnumerationFailed | CaptureError::ResumeFailed => 4,
      };
    },
  };
  let Ok(file) = OpenOptions::new().write(true).create_new(true).open(path) else {
    return 5;
  };
  let mut output = BufWriter::new(file);
  if snapshot
    .write_json(&mut output)
    .and_then(|()| output.flush())
    .is_err()
  {
    drop(output);
    let _ = fs::remove_file(path);
    return 5;
  }
  log::debug!(
    "stack snapshot completed: {} threads",
    snapshot.threads.len()
  );
  0
}
