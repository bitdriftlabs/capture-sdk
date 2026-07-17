// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::open;
use crate::previous::{
  CrashKind,
  NSExceptionCallStack,
  NSExceptionCrashInfo,
  NSExceptionStackFrame,
  PreviousCrashDetails,
  PreviousCrashState,
};
use crate::schema::{self, CrashRecord, CrashRecordHeader, RecordState};
use crate::test_support::test_crash_record_guard;
use crate::writer::CRASH_RECORD;
use anyhow::Result;
use std::ffi::CString;
use std::fs::write;
use std::mem::size_of;
use std::process::id;
use std::slice::from_raw_parts;
use std::sync::atomic::Ordering;

fn crash_record_bytes(record: &CrashRecord) -> &[u8] {
  unsafe { from_raw_parts((&raw const *record).cast::<u8>(), size_of::<CrashRecord>()) }
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
        crc32: 0,
      },
      timestamp_secs: 123,
      ..CrashRecord::default()
    };
    record.nsexception.name[.. 12].copy_from_slice(b"NSException\0");
    record.nsexception.reason[.. 12].copy_from_slice(b"bad reason!\0");
    record.nsexception.call_stack.frame_count = 2;
    record.nsexception.call_stack.frames[0].return_address = 21;
    record.nsexception.call_stack.frames[1].return_address = 34;
    record.header.crc32 = schema::compute_record_checksum(&record);
    write(&path, crash_record_bytes(&record))?;
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
            let mut return_addresses = [0; schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES as usize];
            return_addresses[.. 2].copy_from_slice(&[21, 34]);
            return_addresses
          },
          frames: {
            let mut frames = std::array::from_fn(|_| NSExceptionStackFrame::default());
            frames[0].return_address = 21;
            frames[1].return_address = 34;
            frames
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
  let mut record = CrashRecord {
    header: CrashRecordHeader {
      magic: schema::MAGIC,
      version: schema::VERSION,
      record_state: RecordState::Committed.into(),
      crash_kind: CrashKind::NSException.into(),
      reserved: [0; 2],
      crc32: 0,
    },
    timestamp_secs: 456,
    ..CrashRecord::default()
  };
  record.header.crc32 = schema::compute_record_checksum(&record);
  write(&path, crash_record_bytes(&record))?;

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
  assert_eq!(current_record.pid, id());
  Ok(())
}
