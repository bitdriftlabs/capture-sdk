// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./previous_test.rs"]
mod tests;

use crate::schema::{self, CrashRecord, RawNSExceptionCallStack, RawNSExceptionPayload};
pub(crate) use schema::CrashKind;
use std::mem::size_of;
use std::ptr::read_unaligned;

//
// NSExceptionCallStack
//

#[derive(Clone, Debug, PartialEq, Eq)]
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

//
// NSExceptionCrashInfo
//

#[derive(Clone, Debug, PartialEq, Eq)]
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

//
// PreviousCrashDetails
//

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) enum PreviousCrashDetails {
  #[default]
  None,
  NSException(Box<NSExceptionCrashInfo>),
}

//
// PreviousCrashState
//

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct PreviousCrashState {
  pub(crate) did_crash: bool,
  pub(crate) timestamp_secs: u64,
  pub(crate) pid: u32,
  pub(crate) kind: CrashKind,
  pub(crate) details: PreviousCrashDetails,
}

pub(crate) fn read_previous_state_from_bytes(bytes: &[u8]) -> PreviousCrashState {
  if bytes.len() < size_of::<CrashRecord>() {
    return PreviousCrashState::default();
  }

  // This function defines the acceptance policy for a persisted crash record. The record is
  // mmap-backed, so reads on a later launch must tolerate truncated or partially-corrupt contents
  // instead of assuming the bytes were produced by a clean shutdown.
  let raw: CrashRecord = unsafe { read_unaligned(bytes.as_ptr().cast::<CrashRecord>()) };
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

  if raw.header.record_state != schema::RecordState::Committed {
    log::debug!(
      "ignoring crash record because record_state={} is not committed",
      raw.header.record_state
    );
    return PreviousCrashState::default();
  }

  match raw.header.crash_kind {
    kind if kind == CrashKind::NSException => PreviousCrashState {
      did_crash: true,
      timestamp_secs: raw.timestamp_secs,
      pid: raw.pid,
      kind: CrashKind::NSException,
      details: PreviousCrashDetails::NSException(Box::new(parse_nsexception(&raw.nsexception))),
    },
    other => {
      log::debug!("ignoring crash record with unknown crash_kind={other}");
      PreviousCrashState::default()
    },
  }
}

fn parse_nsexception(raw: &RawNSExceptionPayload) -> NSExceptionCrashInfo {
  // Convert the persisted payload into a safer in-memory representation before exposing it to the
  // rest of the crate. In particular, sanitize stored C strings so malformed persisted bytes are
  // treated as absent data instead of being forwarded as unterminated strings.
  let mut name = raw.name;
  let mut reason = raw.reason;
  sanitize_c_string_bytes(&mut name);
  sanitize_c_string_bytes(&mut reason);

  NSExceptionCrashInfo {
    name,
    reason,
    call_stack: parse_nsexception_call_stack(&raw.call_stack),
  }
}

fn sanitize_c_string_bytes<const N: usize>(bytes: &mut [u8; N]) {
  // Persisted strings are expected to be null-terminated. If a terminator is missing, treat the
  // full field as invalid rather than guessing where a truncated or corrupt string should end.
  if !bytes.contains(&0) {
    bytes.fill(0);
  }
}

fn parse_nsexception_call_stack(raw: &RawNSExceptionCallStack) -> NSExceptionCallStack {
  let max_frame_count =
    u16::try_from(schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES).unwrap_or(u16::MAX);

  NSExceptionCallStack {
    frame_count: raw.frame_count.min(max_frame_count),
    return_addresses: raw.return_addresses,
  }
}
