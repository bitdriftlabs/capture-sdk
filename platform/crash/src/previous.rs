// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::schema::{self, CrashRecord, RawNSExceptionCallStack, RawNSExceptionPayload};

#[repr(u8)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum CrashKind {
  #[default]
  None = schema::CRASH_KIND_NONE,
  NSException = schema::CRASH_KIND_NS_EXCEPTION,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct NSExceptionCallStack {
  pub(crate) frame_count: u16,
  pub(crate) return_addresses: [u64; schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES],
}

impl Default for NSExceptionCallStack {
  fn default() -> Self {
    Self {
      frame_count: 0,
      return_addresses: [0; schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES],
    }
  }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct NSExceptionCrashInfo {
  pub(crate) name: [u8; schema::NS_EXCEPTION_NAME_CAPACITY],
  pub(crate) reason: [u8; schema::NS_EXCEPTION_REASON_CAPACITY],
  pub(crate) call_stack: NSExceptionCallStack,
}

impl Default for NSExceptionCrashInfo {
  fn default() -> Self {
    Self {
      name: [0; schema::NS_EXCEPTION_NAME_CAPACITY],
      reason: [0; schema::NS_EXCEPTION_REASON_CAPACITY],
      call_stack: NSExceptionCallStack::default(),
    }
  }
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum PreviousCrashDetails {
  #[default]
  None,
  NSException(NSExceptionCrashInfo),
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct PreviousCrashState {
  pub(crate) did_crash: bool,
  pub(crate) timestamp_secs: u64,
  pub(crate) pid: u32,
  pub(crate) kind: CrashKind,
  pub(crate) details: PreviousCrashDetails,
}

pub(crate) fn read_previous_state_from_bytes(bytes: &[u8]) -> PreviousCrashState {
  if bytes.len() < std::mem::size_of::<CrashRecord>() {
    return PreviousCrashState::default();
  }

  let raw = unsafe { std::ptr::read_unaligned(bytes.as_ptr().cast::<CrashRecord>()) };
  if raw.header.magic != schema::MAGIC {
    log::debug!("ignoring crash record with unexpected magic");
    return PreviousCrashState::default();
  }

  if raw.header.version != schema::VERSION {
    log::debug!(
      "ignoring crash record with unsupported version {}",
      raw.header.version
    );
    return PreviousCrashState::default();
  }

  if raw.header.record_state != schema::RECORD_STATE_COMMITTED {
    log::debug!(
      "ignoring crash record because record_state={} is not committed",
      raw.header.record_state
    );
    return PreviousCrashState::default();
  }

  match raw.header.crash_kind {
    schema::CRASH_KIND_NS_EXCEPTION => PreviousCrashState {
      did_crash: true,
      timestamp_secs: raw.timestamp_secs,
      pid: raw.pid,
      kind: CrashKind::NSException,
      details: PreviousCrashDetails::NSException(parse_nsexception(raw.nsexception)),
    },
    other => {
      log::debug!("ignoring crash record with unknown crash_kind={other}");
      PreviousCrashState::default()
    },
  }
}

fn parse_nsexception(raw: RawNSExceptionPayload) -> NSExceptionCrashInfo {
  NSExceptionCrashInfo {
    name: raw.name,
    reason: raw.reason,
    call_stack: parse_nsexception_call_stack(raw.call_stack),
  }
}

fn parse_nsexception_call_stack(raw: RawNSExceptionCallStack) -> NSExceptionCallStack {
  let max_frame_count =
    u16::try_from(schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES).unwrap_or(u16::MAX);

  NSExceptionCallStack {
    frame_count: raw.frame_count.min(max_frame_count),
    return_addresses: raw.return_addresses,
  }
}

#[cfg(test)]
mod tests {
  use super::{
    read_previous_state_from_bytes, CrashKind, PreviousCrashDetails, PreviousCrashState,
  };
  use crate::schema::{self, CrashRecord, CrashRecordHeader};

  #[test]
  fn ignores_incomplete_bytes() {
    assert_eq!(
      read_previous_state_from_bytes(&[0; 8]),
      PreviousCrashState::default()
    );
  }

  #[test]
  fn ignores_uncommitted_records() {
    let raw = CrashRecord {
      header: CrashRecordHeader {
        magic: schema::MAGIC,
        version: schema::VERSION,
        record_state: schema::RECORD_STATE_WRITING,
        crash_kind: schema::CRASH_KIND_NS_EXCEPTION,
        reserved: [0; 2],
      },
      ..CrashRecord::default()
    };
    let bytes = unsafe {
      std::slice::from_raw_parts(
        (&raw as *const CrashRecord).cast::<u8>(),
        std::mem::size_of::<CrashRecord>(),
      )
    };

    assert_eq!(
      read_previous_state_from_bytes(bytes),
      PreviousCrashState::default()
    );
  }

  #[test]
  fn reads_committed_nsexception() {
    let mut raw = CrashRecord {
      header: CrashRecordHeader {
        magic: schema::MAGIC,
        version: schema::VERSION,
        record_state: schema::RECORD_STATE_COMMITTED,
        crash_kind: schema::CRASH_KIND_NS_EXCEPTION,
        reserved: [0; 2],
      },
      timestamp_secs: 99,
      pid: 7,
      ..CrashRecord::default()
    };
    raw.nsexception.name[..12].copy_from_slice(b"NSException\0");
    raw.nsexception.reason[..12].copy_from_slice(b"bad reason!\0");
    raw.nsexception.call_stack.frame_count = 2;
    raw.nsexception.call_stack.return_addresses[..2].copy_from_slice(&[10, 11]);

    let bytes = unsafe {
      std::slice::from_raw_parts(
        (&raw as *const CrashRecord).cast::<u8>(),
        std::mem::size_of::<CrashRecord>(),
      )
    };
    let previous = read_previous_state_from_bytes(bytes);

    assert!(previous.did_crash);
    assert_eq!(previous.kind, CrashKind::NSException);
    assert!(matches!(
      previous.details,
      PreviousCrashDetails::NSException(_)
    ));
  }
}
