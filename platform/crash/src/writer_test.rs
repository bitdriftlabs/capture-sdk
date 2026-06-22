// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{prime_shared_record, record_nsexception, CRASH_RECORD};
use crate::schema::{self, CrashKind, RecordState};
use crate::test_support::test_crash_record_guard;
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
