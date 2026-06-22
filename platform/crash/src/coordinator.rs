// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./coordinator_test.rs"]
mod tests;

use crate::monitors;
use crate::previous::PreviousCrashState;
use crate::store::{self, CrashStateStore};
use anyhow::Result;
use std::ffi::CStr;
use std::sync::atomic::{AtomicBool, Ordering};

//
// Coordinator
//

// Owns the persisted crash state store and the monitor lifecycle for a single configured process.
// The coordinator is initialized once, primes the shared mmap-backed crash record for the current
// run, and then installs or removes crash monitors on demand without rebuilding the underlying
// state store.
pub(crate) struct Coordinator {
  previous_crash_state: PreviousCrashState,
  _store: Box<dyn CrashStateStore>,
  started: AtomicBool,
}

impl Coordinator {
  pub(crate) fn new(path: &CStr) -> Result<Self> {
    // Configuration flow:
    // 1. Open the persisted state store at `path`.
    // 2. Parse and cache the previous launch's crash state from the existing mmap contents.
    // 3. Prime a fresh empty record for the current run before any monitor can write into it.
    //
    // This ordering preserves the core invariants for the crate: the previous run is read before
    // the record is reset, and the shared crash record pointer only becomes visible once it points
    // at a live mmap owned by this coordinator.
    let mut store = store::open(path)?;
    let previous_crash_state = store.previous_crash_state();

    log::debug!(
      "loaded previous crash state: did_crash={} timestamp_secs={}",
      previous_crash_state.did_crash,
      previous_crash_state.timestamp_secs
    );

    store.prepare_current_run()?;

    Ok(Self {
      previous_crash_state,
      _store: store,
      started: AtomicBool::new(false),
    })
  }

  pub(crate) fn start(&self) -> bool {
    // Installing the coordinator means installing the crash monitors for the current process. The
    // store has already been prepared in `new()`, so `start()` only has to make the monitor side
    // active and keep that transition idempotent for repeated callers.
    let was_started = self.started.swap(true, Ordering::AcqRel);
    if was_started {
      log::debug!("bitdrift crash coordinator start requested while already started");
      return true;
    }

    let installed = monitors::install();
    if installed {
      log::debug!("installed bitdrift crash monitors");
    } else {
      log::warn!("failed to install bitdrift crash monitors");
      self.started.store(false, Ordering::Release);
    }
    installed
  }

  pub(crate) fn stop(&self) {
    // Stopping the coordinator means uninstalling any process-wide monitors that were previously
    // installed by `start()`. The persisted store intentionally stays alive for the coordinator's
    // lifetime so the shared crash record pointer never outlives the mmap that backs it.
    if self.started.swap(false, Ordering::AcqRel) {
      log::debug!("uninstalling bitdrift crash monitors");
      monitors::uninstall();
    }
  }

  pub(crate) const fn previous_crash_state(&self) -> &PreviousCrashState {
    &self.previous_crash_state
  }
}
