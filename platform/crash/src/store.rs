// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./store_test.rs"]
mod tests;

use crate::previous::{self, PreviousCrashState};
use crate::schema::CrashRecord;
use crate::writer;
use anyhow::{anyhow, Result};
use memmap2::{MmapMut, MmapOptions};
use std::ffi::{CStr, OsStr};
use std::fs::{create_dir_all, OpenOptions};
use std::mem::{align_of, size_of};
use std::os::unix::ffi::OsStrExt as _;
use std::path::Path;

//
// CrashStateStore
//

pub(crate) trait CrashStateStore: Send + Sync {
  fn previous_crash_state(&self) -> PreviousCrashState;
  fn prepare_current_run(&mut self) -> Result<()>;
}

pub(crate) fn open(path: &CStr) -> Result<Box<dyn CrashStateStore>> {
  Ok(Box::new(MmapCrashStateStore::open(path)?))
}

//
// MmapCrashStateStore
//

struct MmapCrashStateStore {
  mapping: MmapMut,
  previous_crash_state: PreviousCrashState,
}

impl MmapCrashStateStore {
  fn open(path: &CStr) -> Result<Self> {
    let path = Path::new(OsStr::from_bytes(path.to_bytes()));

    if let Some(parent) = path.parent() {
      create_dir_all(parent)?;
    }

    let file = OpenOptions::new()
      .create(true)
      .read(true)
      .write(true)
      .truncate(false)
      .open(path)?;
    let desired_len = size_of::<CrashRecord>() as u64;
    let current_len = file.metadata()?.len();

    // The on-disk record may come from an older or newer build. Extend short files so the current
    // schema fits, but do not truncate larger files since callers should still be able to inspect
    // the prefix that matches the current record layout.
    if current_len < desired_len {
      file.set_len(desired_len)?;
    }

    let mapping = unsafe {
      MmapOptions::new()
        .len(size_of::<CrashRecord>())
        .map_mut(&file)?
    };
    let previous_crash_state = previous::read_previous_state_from_bytes(mapping.as_ref());
    log::debug!("opened crash state store at {}", path.display());

    Ok(Self {
      mapping,
      previous_crash_state,
    })
  }
}

impl CrashStateStore for MmapCrashStateStore {
  fn previous_crash_state(&self) -> PreviousCrashState {
    self.previous_crash_state.clone()
  }

  #[allow(clippy::cast_ptr_alignment)]
  fn prepare_current_run(&mut self) -> Result<()> {
    // `mmap` returns page-aligned memory, and this mapping starts at file offset 0, so the start
    // of the mapping is sufficiently aligned for `CrashRecord`.
    let record_ptr = self.mapping.as_mut_ptr().cast::<CrashRecord>();
    if record_ptr.is_null() {
      return Err(anyhow!("crash state mapping returned a null pointer"));
    }
    debug_assert_eq!((record_ptr as usize) % align_of::<CrashRecord>(), 0);

    unsafe {
      writer::prime_shared_record(record_ptr);
    }

    Ok(())
  }
}
