// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::schema::{self, CrashKind, CrashRecord, RecordState};
use std::sync::atomic::{fence, AtomicPtr, Ordering};

pub(crate) static CRASH_RECORD: AtomicPtr<CrashRecord> = AtomicPtr::new(std::ptr::null_mut());

#[cfg(test)]
static TEST_CRASH_RECORD_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

#[cfg(test)]
pub(crate) struct TestCrashRecordGuard {
  _guard: std::sync::MutexGuard<'static, ()>,
}

#[cfg(test)]
impl Drop for TestCrashRecordGuard {
  fn drop(&mut self) {
    CRASH_RECORD.store(std::ptr::null_mut(), Ordering::Release);
  }
}

#[cfg(test)]
pub(crate) fn test_crash_record_guard() -> TestCrashRecordGuard {
  let guard = match TEST_CRASH_RECORD_LOCK.lock() {
    Ok(guard) => guard,
    Err(poisoned) => poisoned.into_inner(),
  };
  CRASH_RECORD.store(std::ptr::null_mut(), Ordering::Release);
  TestCrashRecordGuard { _guard: guard }
}

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
    pid: std::process::id(),
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
  record.pid = std::process::id();

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
  unsafe {
    std::ptr::addr_of_mut!(record.header.record_state)
      .write_volatile(RecordState::Writing.into());
  }
}

// Publish the record only after all payload bytes are visible so the next launch
// never treats a partially-written mmap record as committed.
fn commit_record(record: &mut CrashRecord) {
  fence(Ordering::Release);
  unsafe {
    std::ptr::addr_of_mut!(record.header.record_state)
      .write_volatile(RecordState::Committed.into());
  }
}

fn current_timestamp_secs() -> u64 {
  std::time::SystemTime::now()
    .duration_since(std::time::UNIX_EPOCH)
    .map_or(0, |duration| duration.as_secs())
}

fn write_string<const N: usize>(value: &str, target: &mut [u8; N]) {
  target.fill(0);
  let bytes = value.as_bytes();
  let copy_len = bytes.len().min(target.len().saturating_sub(1));
  target[.. copy_len].copy_from_slice(&bytes[.. copy_len]);
}

#[cfg(test)]
mod tests {
  use super::{prime_shared_record, record_nsexception, test_crash_record_guard, CRASH_RECORD};
  use crate::schema::{self, CrashKind, RecordState};
  use std::sync::atomic::Ordering;

  #[test]
  fn prime_shared_record_initializes_empty_record() {
    let _guard = test_crash_record_guard();
    let mut record = schema::CrashRecord::default();
    unsafe {
      prime_shared_record(&raw mut record);
    }

    assert_eq!(record.header.magic, schema::MAGIC);
    assert_eq!(record.header.record_state, RecordState::Empty);
    assert_eq!(record.header.crash_kind, CrashKind::None);
  }

  #[test]
  fn record_nsexception_commits_after_payload() {
    let _guard = test_crash_record_guard();
    let mut record = schema::CrashRecord::default();
    unsafe {
      prime_shared_record(&raw mut record);
    }

    record_nsexception("NSException", Some("bad reason"), &[1, 2, 3]);

    let record_ptr = CRASH_RECORD.load(Ordering::Acquire);
    let record = unsafe { &*record_ptr };
    assert_eq!(record.header.crash_kind, CrashKind::NSException);
    assert_eq!(record.header.record_state, RecordState::Committed);
    assert_eq!(&record.nsexception.name[.. 12], b"NSException\0");
    assert_eq!(&record.nsexception.reason[.. 11], b"bad reason\0");
    assert_eq!(record.nsexception.call_stack.frame_count, 3);
  }

  #[test]
  fn record_nsexception_clears_reason_when_absent() {
    let _guard = test_crash_record_guard();
    let mut record = schema::CrashRecord::default();
    unsafe {
      prime_shared_record(&raw mut record);
    }

    record_nsexception("NSException", Some("bad reason"), &[]);
    record_nsexception("NSException", None, &[]);

    let record_ptr = CRASH_RECORD.load(Ordering::Acquire);
    let record = unsafe { &*record_ptr };
    assert_eq!(record.nsexception.reason[0], 0);
  }

  #[test]
  fn record_nsexception_truncates_strings_and_null_terminates() {
    let _guard = test_crash_record_guard();
    let mut record = schema::CrashRecord::default();
    unsafe {
      prime_shared_record(&raw mut record);
    }

    let long_name = "a".repeat(schema::NS_EXCEPTION_NAME_CAPACITY * 2);
    let long_reason = "b".repeat(schema::NS_EXCEPTION_REASON_CAPACITY * 2);
    record_nsexception(long_name.as_str(), Some(long_reason.as_str()), &[]);

    let record_ptr = CRASH_RECORD.load(Ordering::Acquire);
    let record = unsafe { &*record_ptr };
    assert_eq!(
      record.nsexception.name[schema::NS_EXCEPTION_NAME_CAPACITY - 1],
      0
    );
    assert_eq!(
      record.nsexception.reason[schema::NS_EXCEPTION_REASON_CAPACITY - 1],
      0
    );
    assert_eq!(
      &record.nsexception.name[.. schema::NS_EXCEPTION_NAME_CAPACITY - 1],
      vec![b'a'; schema::NS_EXCEPTION_NAME_CAPACITY - 1].as_slice()
    );
    assert_eq!(
      &record.nsexception.reason[.. schema::NS_EXCEPTION_REASON_CAPACITY - 1],
      vec![b'b'; schema::NS_EXCEPTION_REASON_CAPACITY - 1].as_slice()
    );
  }

  #[test]
  fn record_nsexception_truncates_return_addresses_to_capacity() {
    let _guard = test_crash_record_guard();
    let mut record = schema::CrashRecord::default();
    unsafe {
      prime_shared_record(&raw mut record);
    }

    let return_addresses = (0 .. schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES + 10)
      .map(|index| index as u64)
      .collect::<Vec<_>>();
    record_nsexception("NSException", None, &return_addresses);

    let record_ptr = CRASH_RECORD.load(Ordering::Acquire);
    let record = unsafe { &*record_ptr };
    assert_eq!(
      record.nsexception.call_stack.frame_count,
      u16::try_from(schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES).unwrap_or(u16::MAX)
    );
    assert_eq!(
      &record.nsexception.call_stack.return_addresses[..],
      &return_addresses[.. schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES]
    );
  }

  #[test]
  fn record_nsexception_clears_unused_return_addresses() {
    let _guard = test_crash_record_guard();
    let mut record = schema::CrashRecord::default();
    unsafe {
      prime_shared_record(&raw mut record);
    }
    record
      .nsexception
      .call_stack
      .return_addresses
      .fill(u64::MAX);

    record_nsexception("NSException", None, &[0x1234, 0x5678]);

    let record_ptr = CRASH_RECORD.load(Ordering::Acquire);
    let record = unsafe { &*record_ptr };
    assert_eq!(record.nsexception.call_stack.frame_count, 2);
    assert_eq!(
      &record.nsexception.call_stack.return_addresses[.. 2],
      &[0x1234, 0x5678]
    );
    assert_eq!(record.nsexception.call_stack.return_addresses[2], 0);
  }
}
