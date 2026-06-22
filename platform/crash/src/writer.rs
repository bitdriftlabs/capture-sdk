// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./writer_test.rs"]
mod tests;

use crate::schema::{self, CrashKind, CrashRecord, RecordState};
use std::process::id;
use std::ptr::{addr_of_mut, null_mut};
use std::sync::atomic::{fence, AtomicPtr, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

//
// Shared crash record state
//

pub(crate) static CRASH_RECORD: AtomicPtr<CrashRecord> = AtomicPtr::new(null_mut());

pub(crate) unsafe fn prime_shared_record(record_ptr: *mut CrashRecord) {
  let record = unsafe { &mut *record_ptr };
  *record = CrashRecord {
    header: schema::CrashRecordHeader {
      magic: schema::MAGIC,
      version: schema::VERSION,
      record_state: RecordState::Empty.into(),
      crash_kind: CrashKind::None.into(),
      reserved: [0; 2],
    },
    timestamp_secs: 0,
    pid: id(),
    reserved: [0; 4],
    nsexception: schema::RawNSExceptionPayload::default(),
  };
  CRASH_RECORD.store(record_ptr, Ordering::Release);
}

pub(crate) fn record_nsexception(name: &str, reason: Option<&str>, return_addresses: &[u64]) {
  let record_ptr = CRASH_RECORD.load(Ordering::Acquire);
  if record_ptr.is_null() {
    return;
  }

  let record = unsafe { &mut *record_ptr };
  mark_record_writing(record);
  record.timestamp_secs = current_timestamp_secs();
  record.pid = id();

  write_string(name, &mut record.nsexception.name);
  if let Some(reason) = reason {
    write_string(reason, &mut record.nsexception.reason);
  } else {
    record.nsexception.reason.fill(0);
  }
  record.nsexception.call_stack.frame_count = return_addresses
    .len()
    .min(schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES)
    .try_into()
    .ok()
    .unwrap_or(u16::MAX);
  record.nsexception.call_stack.return_addresses.fill(0);
  let copy_len = usize::from(record.nsexception.call_stack.frame_count);
  record.nsexception.call_stack.return_addresses[.. copy_len]
    .copy_from_slice(&return_addresses[.. copy_len]);
  record.header.crash_kind = CrashKind::NSException.into();
  commit_record(record);
}

fn mark_record_writing(record: &mut CrashRecord) {
  // The mapped record is shared with a future process, so state transitions must be emitted as
  // observable writes instead of relying on compiler-visible ordinary stores.
  unsafe {
    addr_of_mut!(record.header.record_state).write_volatile(RecordState::Writing.into());
  }
}

// Publish the record only after all payload bytes are visible so the next launch never treats a
// partially-written mmap record as committed.
fn commit_record(record: &mut CrashRecord) {
  fence(Ordering::Release);
  unsafe {
    addr_of_mut!(record.header.record_state).write_volatile(RecordState::Committed.into());
  }
}

fn current_timestamp_secs() -> u64 {
  SystemTime::now()
    .duration_since(UNIX_EPOCH)
    .map_or(0, |duration| duration.as_secs())
}

fn write_string<const N: usize>(value: &str, target: &mut [u8; N]) {
  target.fill(0);
  let bytes = value.as_bytes();
  let copy_len = bytes.len().min(target.len().saturating_sub(1));
  target[.. copy_len].copy_from_slice(&bytes[.. copy_len]);
}
