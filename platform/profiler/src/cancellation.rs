// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use crate::CaptureError;
use std::sync::atomic::{AtomicBool, Ordering};

//
// CancellationToken
//

/// A cancellation request only publishes an atomic flag. It never waits, takes a lock, invokes a
/// callback or resumes a thread. The collector checks it only outside suspended-thread sections.
#[derive(Default)]
pub struct CancellationToken {
  cancelled: AtomicBool,
}

impl CancellationToken {
  pub fn cancel(&self) {
    self.cancelled.store(true, Ordering::Relaxed);
  }

  #[must_use]
  pub fn is_cancelled(&self) -> bool {
    self.cancelled.load(Ordering::Relaxed)
  }

  pub(crate) fn check(&self) -> Result<(), CaptureError> {
    if self.is_cancelled() {
      return Err(CaptureError::Cancelled);
    }
    Ok(())
  }
}
