// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
//! Command-driven ARM64 stack snapshots with Firefox Profiler JSON output.

#[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
mod apple;
mod json;
#[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
mod mach;
#[cfg(test)]
#[path = "./lib_test.rs"]
mod tests;

use bd_unwind::aarch64::{Frame, StopReason};
use bd_unwind::image::ImageInfo;
use std::io::{self, Write};
use std::sync::atomic::{AtomicBool, Ordering};

static CAPTURING: AtomicBool = AtomicBool::new(false);

//
// CaptureError
//

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CaptureError {
  UnsupportedArchitecture,
  AlreadyCapturing,
  InvalidBufferSize,
  ThreadEnumerationFailed,
  ResumeFailed,
}

//
// CaptureGuard
//

struct CaptureGuard;

impl CaptureGuard {
  fn acquire() -> Result<Self, CaptureError> {
    CAPTURING
      .compare_exchange(false, true, Ordering::Acquire, Ordering::Relaxed)
      .map(|_| Self)
      .map_err(|_| CaptureError::AlreadyCapturing)
  }
}

impl Drop for CaptureGuard {
  fn drop(&mut self) {
    CAPTURING.store(false, Ordering::Release);
  }
}

//
// ThreadSnapshot
//

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ThreadStatus {
  Captured(StopReason),
  CaptureThread,
  SuspendFailed,
  RegistersUnavailable,
  StackUnavailable,
}

pub struct ThreadSnapshot {
  pub native_id: u64,
  pub name: String,
  pub status: ThreadStatus,
  pub frames: Vec<Frame>,
  pub stack_truncated: bool,
  pub copied_stack_bytes: usize,
  pub timestamp_ns: u64,
  pub suspension_ns: u64,
}

//
// Snapshot
//

pub struct Snapshot {
  pub pid: u32,
  pub threads: Vec<ThreadSnapshot>,
  pub images: Vec<ImageInfo>,
}

impl Snapshot {
  /// Writes a Firefox Profiler JSON snapshot. Samples represent one sequential pass over threads,
  /// not CPU time or simultaneous observations. Native IDs and capture outcomes accompany each
  /// thread; imported thread IDs are local to this file because Perfetto accepts 32-bit IDs.
  pub fn write_json(&self, output: &mut impl Write) -> io::Result<()> {
    json::write(self, output)
  }
}

/// Captures one sequential pass over the current task's threads, excluding the calling thread.
/// `buffer_size_kb` (16..=16384 KiB) bounds stack scratch space plus retained frame storage. Thread
/// metadata and streamed JSON are outside that budget. No allocation or logging occurs while a
/// thread is suspended. Only one capture may run at a time in this process.
pub fn capture(buffer_size_kb: u32) -> Result<Snapshot, CaptureError> {
  if !(16 ..= 16_384).contains(&buffer_size_kb) {
    return Err(CaptureError::InvalidBufferSize);
  }
  let _guard = CaptureGuard::acquire()?;
  log::debug!("starting stack snapshot with {buffer_size_kb} KiB capture buffer");
  #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
  {
    apple::capture(buffer_size_kb as usize * 1024)
  }
  #[cfg(not(all(target_vendor = "apple", target_arch = "aarch64")))]
  Err(CaptureError::UnsupportedArchitecture)
}
