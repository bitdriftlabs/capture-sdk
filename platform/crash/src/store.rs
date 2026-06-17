// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::state::{self, CrashState, PreviousCrashState};
use anyhow::{anyhow, Result};
use memmap2::{MmapMut, MmapOptions};
use std::ffi::CStr;
use std::fs::OpenOptions;
use std::os::unix::ffi::OsStrExt as _;
use std::path::Path;

pub(crate) trait CrashStateStore: Send + Sync {
  fn previous_crash_state(&self) -> PreviousCrashState;
  fn prepare_current_run(&mut self) -> Result<()>;
}

pub(crate) fn open(path: &CStr) -> Result<Box<dyn CrashStateStore>> {
  Ok(Box::new(MmapCrashStateStore::open(path)?))
}

struct MmapCrashStateStore {
  mapping: MmapMut,
  previous_crash_state: PreviousCrashState,
}

impl MmapCrashStateStore {
  fn open(path: &CStr) -> Result<Self> {
    let path = Path::new(std::ffi::OsStr::from_bytes(path.to_bytes()));
    if let Some(parent) = path.parent() {
      std::fs::create_dir_all(parent)?;
    }

    let file = OpenOptions::new()
      .create(true)
      .read(true)
      .write(true)
      .truncate(false)
      .open(path)?;
    file.set_len(std::mem::size_of::<CrashState>() as u64)?;

    let mapping = unsafe {
      MmapOptions::new()
        .len(std::mem::size_of::<CrashState>())
        .map_mut(&file)?
    };
    let previous_crash_state =
      state::previous_crash_state(unsafe { &*mapping.as_ptr().cast::<CrashState>() });

    Ok(Self {
      mapping,
      previous_crash_state,
    })
  }
}

impl CrashStateStore for MmapCrashStateStore {
  fn previous_crash_state(&self) -> PreviousCrashState {
    self.previous_crash_state
  }

  fn prepare_current_run(&mut self) -> Result<()> {
    let state_ptr = self.mapping.as_mut_ptr().cast::<CrashState>();
    if state_ptr.is_null() {
      return Err(anyhow!("crash state mapping returned a null pointer"));
    }

    unsafe {
      state::prime_shared_state(state_ptr);
    }

    Ok(())
  }
}

#[cfg(test)]
mod tests {
  use super::open;
  use crate::state::{
    self,
    CrashState,
    CrashStateHeader,
    CrashStateKind,
    NSExceptionCrashInfo,
    NSExceptionCallStack,
    PreviousCrashDetails,
  };
  use std::ffi::CString;

  #[test]
  fn open_creates_store_for_empty_file() {
    let tempdir = tempfile::tempdir().unwrap();
    let path = CString::new(
      tempdir
        .path()
        .join("state.bin")
        .to_string_lossy()
        .as_bytes(),
    )
    .unwrap();

    let store = open(&path).unwrap();

    assert_eq!(
      store.previous_crash_state(),
      state::PreviousCrashState::default()
    );
  }

  #[test]
  fn open_reads_previous_nsexception_state() {
    let tempdir = tempfile::tempdir().unwrap();
    let path = tempdir.path().join("state.bin");

    {
      let mut state = CrashState::default();
      state.header = CrashStateHeader {
        magic: state::MAGIC,
        version: state::VERSION,
        state: state::STATE_CRASHED,
        kind: CrashStateKind::NSException,
      };
      state.timestamp_secs = 123;
      state.nsexception.name[.. 12].copy_from_slice(b"NSException\0");
      state.nsexception.reason[.. 12].copy_from_slice(b"bad reason!\0");
      state.nsexception.call_stack.frame_count = 2;
      state.nsexception.call_stack.return_addresses[..2].copy_from_slice(&[21, 34]);
      std::fs::write(&path, unsafe {
        std::slice::from_raw_parts(
          (&state as *const CrashState).cast::<u8>(),
          std::mem::size_of::<CrashState>(),
        )
      })
      .unwrap();
    }

    let path = CString::new(path.to_string_lossy().as_bytes()).unwrap();
    let store = open(&path).unwrap();

    assert_eq!(
      store.previous_crash_state().details,
      PreviousCrashDetails::NSException(NSExceptionCrashInfo {
        name: {
          let mut name = [0; state::NS_EXCEPTION_NAME_CAPACITY];
          name[.. 12].copy_from_slice(b"NSException\0");
          name
        },
        reason: {
          let mut reason = [0; state::NS_EXCEPTION_REASON_CAPACITY];
          reason[.. 12].copy_from_slice(b"bad reason!\0");
          reason
        },
        call_stack: NSExceptionCallStack {
          frame_count: 2,
          return_addresses: {
            let mut return_addresses = [0; state::MAX_NS_EXCEPTION_CALL_STACK_FRAMES];
            return_addresses[..2].copy_from_slice(&[21, 34]);
            return_addresses
          },
        },
      })
    );
  }
}
