// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#[cfg(test)]
#[path = "./cancellation_test.rs"]
mod tests;

use crate::CaptureError;
use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, Ordering};
#[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
use std::thread;
use std::thread::Thread;
#[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
use std::time::Instant;

//
// CancellationToken
//

/// A token belongs to one request and its sampling worker. Cancellation publishes an atomic flag
/// and unparks the sampling worker, if registered. It never waits, takes a lock or resumes a target
/// thread. Checks and waits run only outside suspension.
#[derive(Default)]
pub struct CancellationToken {
  cancelled: AtomicBool,
  worker: OnceLock<Thread>,
}

impl CancellationToken {
  pub fn cancel(&self) {
    self.cancelled.store(true, Ordering::Release);
    if let Some(worker) = self.worker.get() {
      worker.unpark();
    }
  }

  #[must_use]
  pub fn is_cancelled(&self) -> bool {
    self.cancelled.load(Ordering::Acquire)
  }

  /// Only the request's worker calls wait. Registration precedes the flag check so cancellation
  /// cannot be lost between checking and parking; an early unpark leaves a token for the next park.
  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  pub(crate) fn wait(&self, deadline: Instant) -> Result<(), CaptureError> {
    self.worker.get_or_init(thread::current);
    loop {
      self.check()?;
      let remaining = deadline.saturating_duration_since(Instant::now());
      if remaining.is_zero() {
        return Ok(());
      }
      thread::park_timeout(remaining);
    }
  }

  pub(crate) fn check(&self) -> Result<(), CaptureError> {
    if self.is_cancelled() {
      return Err(CaptureError::Cancelled);
    }
    Ok(())
  }
}
