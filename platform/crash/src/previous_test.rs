// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{
  read_previous_state_from_bytes,
  CrashKind,
  NSExceptionCallStack,
  NSExceptionStackFrame,
  PreviousCrashDetails,
  PreviousCrashState,
};
use crate::schema::{self, CrashRecord, CrashRecordHeader, RecordState};
use std::mem::size_of;
use std::slice::from_raw_parts;

fn crash_record_bytes(record: &CrashRecord) -> &[u8] {
  unsafe { from_raw_parts((&raw const *record).cast::<u8>(), size_of::<CrashRecord>()) }
}

fn committed_nsexception_record() -> CrashRecord {
  CrashRecord {
    header: CrashRecordHeader {
      magic: schema::MAGIC,
      version: schema::VERSION,
      record_state: RecordState::Committed.into(),
      crash_kind: CrashKind::NSException.into(),
      reserved: [0; 2],
      crc32: 0,
    },
    timestamp_secs: 99,
    pid: 7,
    ..CrashRecord::default()
  }
}

fn finalize_crc32(record: &mut CrashRecord) {
  record.header.crc32 = schema::compute_record_checksum(record);
}

#[test]
fn ignores_incomplete_bytes() {
  assert_eq!(
    read_previous_state_from_bytes(&[0; 8]),
    PreviousCrashState::default()
  );
}

#[test]
fn ignores_record_with_unexpected_magic() {
  let raw = CrashRecord {
    header: CrashRecordHeader {
      magic: 0,
      version: schema::VERSION,
      record_state: RecordState::Committed.into(),
      crash_kind: CrashKind::NSException.into(),
      reserved: [0; 2],
      crc32: 0,
    },
    ..CrashRecord::default()
  };

  assert_eq!(
    read_previous_state_from_bytes(crash_record_bytes(&raw)),
    PreviousCrashState::default()
  );
}

#[test]
fn ignores_record_with_unsupported_version() {
  let raw = CrashRecord {
    header: CrashRecordHeader {
      magic: schema::MAGIC,
      version: schema::VERSION + 1,
      record_state: RecordState::Committed.into(),
      crash_kind: CrashKind::NSException.into(),
      reserved: [0; 2],
      crc32: 0,
    },
    ..CrashRecord::default()
  };

  assert_eq!(
    read_previous_state_from_bytes(crash_record_bytes(&raw)),
    PreviousCrashState::default()
  );
}

#[test]
fn ignores_uncommitted_records() {
  let raw = CrashRecord {
    header: CrashRecordHeader {
      magic: schema::MAGIC,
      version: schema::VERSION,
      record_state: RecordState::Writing.into(),
      crash_kind: CrashKind::NSException.into(),
      reserved: [0; 2],
      crc32: 0,
    },
    ..CrashRecord::default()
  };

  assert_eq!(
    read_previous_state_from_bytes(crash_record_bytes(&raw)),
    PreviousCrashState::default()
  );
}

#[test]
fn ignores_record_with_crc32_mismatch() {
  let mut raw = committed_nsexception_record();
  raw.nsexception.name[.. 12].copy_from_slice(b"NSException\0");
  finalize_crc32(&mut raw);
  raw.nsexception.name[0] = b'X';

  assert_eq!(
    read_previous_state_from_bytes(crash_record_bytes(&raw)),
    PreviousCrashState::default()
  );
}

#[test]
fn ignores_unknown_crash_kind() {
  let raw = CrashRecord {
    header: CrashRecordHeader {
      magic: schema::MAGIC,
      version: schema::VERSION,
      record_state: RecordState::Committed.into(),
      crash_kind: 255,
      reserved: [0; 2],
      crc32: 0,
    },
    ..CrashRecord::default()
  };

  assert_eq!(
    read_previous_state_from_bytes(crash_record_bytes(&raw)),
    PreviousCrashState::default()
  );
}

#[test]
fn reads_committed_nsexception() {
  let mut raw = committed_nsexception_record();
  raw.nsexception.name[.. 12].copy_from_slice(b"NSException\0");
  raw.nsexception.reason[.. 12].copy_from_slice(b"bad reason!\0");
  raw.nsexception.call_stack.frame_count = 2;
  raw.nsexception.call_stack.frames[0].return_address = 10;
  raw.nsexception.call_stack.frames[1].return_address = 11;
  raw.nsexception.call_stack.frames[0].image_load_address = 0x1000;
  raw.nsexception.call_stack.frames[0].binary_name[.. 6].copy_from_slice(b"MyApp\0");
  raw.nsexception.call_stack.frames[0].image_id[.. schema::NS_EXCEPTION_IMAGE_ID_CAPACITY]
    .copy_from_slice(b"BD9C11B4-BF87-3F60-AEA0-0141BD7F8AC0\0");
  finalize_crc32(&mut raw);

  let previous = read_previous_state_from_bytes(crash_record_bytes(&raw));

  assert!(previous.did_crash);
  assert_eq!(previous.timestamp_secs, 99);
  assert_eq!(previous.pid, 7);
  assert_eq!(previous.kind, CrashKind::NSException);
  assert!(matches!(
    &previous.details,
    PreviousCrashDetails::NSException(_)
  ));
  let exception = match previous.details {
    PreviousCrashDetails::NSException(exception) => exception,
    PreviousCrashDetails::None => return,
  };
  assert_eq!(&exception.name[.. 12], b"NSException\0");
  assert_eq!(&exception.reason[.. 12], b"bad reason!\0");
  assert_eq!(
    exception.call_stack,
    NSExceptionCallStack {
      frame_count: 2,
      return_addresses: {
        let mut return_addresses = [0; schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES as usize];
        return_addresses[.. 2].copy_from_slice(&[10, 11]);
        return_addresses
      },
      frames: {
        let mut frames = std::array::from_fn(|_| NSExceptionStackFrame::default());
        frames[0] = NSExceptionStackFrame {
          return_address: 10,
          image_load_address: 0x1000,
          binary_name: {
            let mut binary_name = [0; schema::NS_EXCEPTION_BINARY_NAME_CAPACITY];
            binary_name[.. 6].copy_from_slice(b"MyApp\0");
            binary_name
          },
          image_id: {
            let mut image_id = [0; schema::NS_EXCEPTION_IMAGE_ID_CAPACITY];
            image_id[.. schema::NS_EXCEPTION_IMAGE_ID_CAPACITY]
              .copy_from_slice(b"BD9C11B4-BF87-3F60-AEA0-0141BD7F8AC0\0");
            image_id
          },
        };
        frames[1].return_address = 11;
        frames
      },
    }
  );
}

#[test]
fn clamps_nsexception_frame_count_to_capacity() {
  let mut raw = committed_nsexception_record();
  raw.nsexception.call_stack.frame_count = u16::MAX;
  finalize_crc32(&mut raw);

  let previous = read_previous_state_from_bytes(crash_record_bytes(&raw));

  assert!(matches!(
    &previous.details,
    PreviousCrashDetails::NSException(_)
  ));
  let exception = match previous.details {
    PreviousCrashDetails::NSException(exception) => exception,
    PreviousCrashDetails::None => return,
  };
  assert_eq!(
    exception.call_stack.frame_count,
    schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES
  );
}

#[test]
fn clears_unterminated_nsexception_strings() {
  let mut raw = committed_nsexception_record();
  raw.nsexception.name.fill(b'A');
  raw.nsexception.reason.fill(b'B');
  finalize_crc32(&mut raw);

  let previous = read_previous_state_from_bytes(crash_record_bytes(&raw));

  assert!(matches!(
    &previous.details,
    PreviousCrashDetails::NSException(_)
  ));
  let exception = match previous.details {
    PreviousCrashDetails::NSException(exception) => exception,
    PreviousCrashDetails::None => return,
  };
  assert_eq!(exception.name[0], 0);
  assert_eq!(exception.reason[0], 0);
}

#[test]
fn clears_unterminated_frame_metadata_strings() {
  let mut raw = committed_nsexception_record();
  raw.nsexception.call_stack.frame_count = 1;
  raw.nsexception.call_stack.frames[0].return_address = 10;
  raw.nsexception.call_stack.frames[0].binary_name.fill(b'A');
  raw.nsexception.call_stack.frames[0].image_id.fill(b'B');
  finalize_crc32(&mut raw);

  let previous = read_previous_state_from_bytes(crash_record_bytes(&raw));

  assert!(matches!(
    &previous.details,
    PreviousCrashDetails::NSException(_)
  ));
  let exception = match previous.details {
    PreviousCrashDetails::NSException(exception) => exception,
    PreviousCrashDetails::None => return,
  };
  assert_eq!(exception.call_stack.frames[0].binary_name[0], 0);
  assert_eq!(exception.call_stack.frames[0].image_id[0], 0);
}
