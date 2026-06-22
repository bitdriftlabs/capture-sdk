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

pub(crate) struct Coordinator {
  previous_crash_state: PreviousCrashState,
  _store: Box<dyn CrashStateStore>,
  started: AtomicBool,
}

impl Coordinator {
  pub(crate) fn new(path: &CStr) -> Result<Self> {
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
    if self.started.swap(false, Ordering::AcqRel) {
      log::debug!("uninstalling bitdrift crash monitors");
      monitors::uninstall();
    }
  }

  pub(crate) const fn previous_crash_state(&self) -> &PreviousCrashState {
    &self.previous_crash_state
  }
}
