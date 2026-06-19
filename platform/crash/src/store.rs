// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::previous::{self, PreviousCrashState};
use crate::schema::CrashRecord;
use crate::writer;
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
    let desired_len = std::mem::size_of::<CrashRecord>() as u64;
    let current_len = file.metadata()?.len();
    if current_len < desired_len {
      file.set_len(desired_len)?;
    }

    let mapping = unsafe {
      MmapOptions::new()
        .len(std::mem::size_of::<CrashRecord>())
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
    // `mmap` returns page-aligned memory, and this mapping starts at file offset 0,
    // so the start of the mapping is sufficiently aligned for `CrashRecord`.
    let record_ptr = self.mapping.as_mut_ptr().cast::<CrashRecord>();
    if record_ptr.is_null() {
      return Err(anyhow!("crash state mapping returned a null pointer"));
    }
    debug_assert_eq!(
      (record_ptr as usize) % std::mem::align_of::<CrashRecord>(),
      0
    );

    unsafe {
      writer::prime_shared_record(record_ptr);
    }

    Ok(())
  }
}

#[cfg(test)]
mod tests {
  use super::open;
  use crate::previous::{
    CrashKind,
    NSExceptionCallStack,
    NSExceptionCrashInfo,
    PreviousCrashDetails,
    PreviousCrashState,
  };
  use crate::schema::{self, CrashRecord, CrashRecordHeader, RecordState};
  use crate::writer::{test_crash_record_guard, CRASH_RECORD};
  use anyhow::Result;
  use std::ffi::CString;
  use std::sync::atomic::Ordering;

  fn crash_record_bytes(record: &CrashRecord) -> &[u8] {
    unsafe {
      std::slice::from_raw_parts(
        (&raw const *record).cast::<u8>(),
        std::mem::size_of::<CrashRecord>(),
      )
    }
  }

  #[test]
  fn open_creates_store_for_empty_file() -> Result<()> {
    let _guard = test_crash_record_guard();
    let tempdir = tempfile::tempdir()?;
    let path = CString::new(
      tempdir
        .path()
        .join("state.bin")
        .to_string_lossy()
        .as_bytes(),
    )?;

    let store = open(&path)?;

    assert_eq!(store.previous_crash_state(), PreviousCrashState::default());
    Ok(())
  }

  #[test]
  fn open_reads_previous_nsexception_state() -> Result<()> {
    let _guard = test_crash_record_guard();
    let tempdir = tempfile::tempdir()?;
    let path = tempdir.path().join("state.bin");

    {
      let mut record = CrashRecord {
        header: CrashRecordHeader {
          magic: schema::MAGIC,
          version: schema::VERSION,
          record_state: RecordState::Committed.into(),
          crash_kind: CrashKind::NSException.into(),
          reserved: [0; 2],
        },
        timestamp_secs: 123,
        ..CrashRecord::default()
      };
      record.nsexception.name[.. 12].copy_from_slice(b"NSException\0");
      record.nsexception.reason[.. 12].copy_from_slice(b"bad reason!\0");
      record.nsexception.call_stack.frame_count = 2;
      record.nsexception.call_stack.return_addresses[.. 2].copy_from_slice(&[21, 34]);
      std::fs::write(&path, crash_record_bytes(&record))?;
    }

    let path = CString::new(path.to_string_lossy().as_bytes())?;
    let store = open(&path)?;

    assert_eq!(
      store.previous_crash_state(),
      PreviousCrashState {
        did_crash: true,
        timestamp_secs: 123,
        pid: 0,
        kind: CrashKind::NSException,
        details: PreviousCrashDetails::NSException(Box::new(NSExceptionCrashInfo {
          name: {
            let mut name = [0; schema::NS_EXCEPTION_NAME_CAPACITY];
            name[.. 12].copy_from_slice(b"NSException\0");
            name
          },
          reason: {
            let mut reason = [0; schema::NS_EXCEPTION_REASON_CAPACITY];
            reason[.. 12].copy_from_slice(b"bad reason!\0");
            reason
          },
          call_stack: NSExceptionCallStack {
            frame_count: 2,
            return_addresses: {
              let mut return_addresses = [0; schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES];
              return_addresses[.. 2].copy_from_slice(&[21, 34]);
              return_addresses
            },
          },
        })),
      }
    );
    Ok(())
  }

  #[test]
  fn open_prepares_empty_record_for_current_run_after_reading_previous_state() -> Result<()> {
    let _guard = test_crash_record_guard();
    let tempdir = tempfile::tempdir()?;
    let path = tempdir.path().join("state.bin");
    let record = CrashRecord {
      header: CrashRecordHeader {
        magic: schema::MAGIC,
        version: schema::VERSION,
        record_state: RecordState::Committed.into(),
        crash_kind: CrashKind::NSException.into(),
        reserved: [0; 2],
      },
      timestamp_secs: 456,
      ..CrashRecord::default()
    };
    std::fs::write(&path, crash_record_bytes(&record))?;

    let path = CString::new(path.to_string_lossy().as_bytes())?;
    let mut store = open(&path)?;

    assert!(store.previous_crash_state().did_crash);
    assert_eq!(store.previous_crash_state().timestamp_secs, 456);

    store.prepare_current_run()?;

    let record_ptr = CRASH_RECORD.load(Ordering::Acquire);
    let current_record = unsafe { &*record_ptr };
    assert_eq!(current_record.header.magic, schema::MAGIC);
    assert_eq!(current_record.header.record_state, RecordState::Empty);
    assert_eq!(current_record.header.crash_kind, CrashKind::None);
    assert_eq!(current_record.timestamp_secs, 0);
    assert_eq!(current_record.pid, std::process::id());
    Ok(())
  }
}
