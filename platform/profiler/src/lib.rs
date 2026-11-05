// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
//! Command-driven ARM64 stack snapshots with Firefox Profiler JSON output.

#[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
mod apple;
mod cancellation;
mod json;
#[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
mod mach;
mod output;
#[cfg(test)]
#[path = "./lib_test.rs"]
mod tests;

use bd_unwind::aarch64::{Frame, StopReason};
use bd_unwind::image::{ImageError, ImageInfo};
pub use cancellation::CancellationToken;
pub use output::write_json;
use std::collections::BTreeMap;
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
  Cancelled,
  NoUsefulStacks,
}

//
// Profiler
//

/// Owns the process-wide snapshot request slot. Keep it alive through attachment export.
pub struct Profiler {
  buffer_size_kb: u32,
}

impl Profiler {
  pub fn new(buffer_size_kb: u32, cancellation: &CancellationToken) -> Result<Self, CaptureError> {
    cancellation.check()?;
    if buffer_size_kb == 0 {
      return Err(CaptureError::InvalidBufferSize);
    }
    CAPTURING
      .compare_exchange(false, true, Ordering::Acquire, Ordering::Relaxed)
      .map(|_| Self { buffer_size_kb })
      .map_err(|_| CaptureError::AlreadyCapturing)
  }

  /// Captures one sequential pass over threads, excluding the calling thread. The buffer bounds
  /// scratch space plus retained frames; metadata and encoded output are outside that budget.
  /// No allocation or logging occurs while a target thread is suspended.
  pub fn capture(&mut self, cancellation: &CancellationToken) -> Result<Snapshot, CaptureError> {
    cancellation.check()?;
    log::debug!(
      "starting stack snapshot with {} KiB capture buffer",
      self.buffer_size_kb
    );
    #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
    {
      apple::capture(self.buffer_size_kb as usize * 1024, cancellation)
    }
    #[cfg(not(all(target_vendor = "apple", target_arch = "aarch64")))]
    Err(CaptureError::UnsupportedArchitecture)
  }
}

impl Drop for Profiler {
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
  /// Counts rejected images by parser error, surveyed after all target threads are resumed.
  pub image_errors: BTreeMap<ImageError, usize>,
}

impl Snapshot {
  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  pub(crate) fn record_image(&mut self, result: Result<ImageInfo, ImageError>) {
    match result {
      Ok(image) => self.images.push(image),
      Err(error) => *self.image_errors.entry(error).or_default() += 1,
    }
  }

  #[must_use]
  pub fn summary(&self) -> Summary {
    let mut summary = Summary::default();
    for thread in &self.threads {
      if thread.status == ThreadStatus::CaptureThread {
        summary.skipped_threads += 1;
      } else if thread.frames.iter().any(|frame| frame.address != 0) {
        summary.captured_threads += 1;
        if thread.stack_truncated || thread.status != ThreadStatus::Captured(StopReason::EndOfStack)
        {
          summary.truncated_threads += 1;
        }
      } else {
        summary.failed_threads += 1;
      }
    }
    summary
  }

  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  pub(crate) fn ensure_useful(&self) -> Result<(), CaptureError> {
    if self.summary().captured_threads == 0 {
      return Err(CaptureError::NoUsefulStacks);
    }
    Ok(())
  }
}

//
// Summary
//

/// Counts partition threads into captured (including partial), failed and skipped. Truncated
/// threads are a subset of captured threads whose stack copy or walk did not complete.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct Summary {
  pub captured_threads: usize,
  pub failed_threads: usize,
  pub truncated_threads: usize,
  pub skipped_threads: usize,
}
