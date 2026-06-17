// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use std::sync::atomic::{AtomicPtr, Ordering};

pub(crate) const MAGIC: u64 = u64::from_be_bytes(*b"BDCRASH\0");
pub(crate) const VERSION: u32 = 1;
pub(crate) const STATE_RUNNING: u8 = 1;
pub(crate) const STATE_CRASHED: u8 = 2;
pub(crate) const NS_EXCEPTION_NAME_CAPACITY: usize = 128;
pub(crate) const NS_EXCEPTION_REASON_CAPACITY: usize = 512;
pub(crate) const MAX_NS_EXCEPTION_CALL_STACK_FRAMES: usize = 64;

#[repr(u8)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum CrashStateKind {
  #[default]
  None        = 0,
  NSException = 1,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct CrashStateHeader {
  pub(crate) magic: u64,
  pub(crate) version: u32,
  pub(crate) state: u8,
  pub(crate) kind: CrashStateKind,
}

impl CrashStateHeader {
  pub(crate) fn is_valid(self) -> bool {
    self.magic == MAGIC && self.version == VERSION
  }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct NSExceptionCallStack {
  pub(crate) frame_count: u16,
  pub(crate) return_addresses: [u64; MAX_NS_EXCEPTION_CALL_STACK_FRAMES],
}

impl Default for NSExceptionCallStack {
  fn default() -> Self {
    Self {
      frame_count: 0,
      return_addresses: [0; MAX_NS_EXCEPTION_CALL_STACK_FRAMES],
    }
  }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct NSExceptionCrashInfo {
  pub(crate) name: [u8; NS_EXCEPTION_NAME_CAPACITY],
  pub(crate) reason: [u8; NS_EXCEPTION_REASON_CAPACITY],
  pub(crate) call_stack: NSExceptionCallStack,
}

impl Default for NSExceptionCrashInfo {
  fn default() -> Self {
    Self {
      name: [0; NS_EXCEPTION_NAME_CAPACITY],
      reason: [0; NS_EXCEPTION_REASON_CAPACITY],
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
  pub(crate) details: PreviousCrashDetails,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct CrashState {
  pub(crate) header: CrashStateHeader,
  pub(crate) timestamp_secs: u64,
  pub(crate) pid: u32,
  pub(crate) nsexception: NSExceptionCrashInfo,
}

impl Default for CrashState {
  fn default() -> Self {
    Self {
      header: CrashStateHeader::default(),
      timestamp_secs: 0,
      pid: 0,
      nsexception: NSExceptionCrashInfo::default(),
    }
  }
}

pub(crate) static CRASH_STATE: AtomicPtr<CrashState> = AtomicPtr::new(std::ptr::null_mut());

pub(crate) fn previous_crash_state(state: &CrashState) -> PreviousCrashState {
  let is_crashed = state.header.is_valid()
    && state.header.state == STATE_CRASHED
    && state.header.kind == CrashStateKind::NSException;

  if !is_crashed {
    return PreviousCrashState::default();
  }

  PreviousCrashState {
    did_crash: true,
    timestamp_secs: state.timestamp_secs,
    details: PreviousCrashDetails::NSException(state.nsexception),
  }
}

pub(crate) unsafe fn prime_shared_state(state_ptr: *mut CrashState) {
  let state = unsafe { &mut *state_ptr };
  *state = CrashState {
    header: CrashStateHeader {
      magic: MAGIC,
      version: VERSION,
      state: STATE_RUNNING,
      kind: CrashStateKind::None,
    },
    timestamp_secs: 0,
    pid: std::process::id(),
    nsexception: NSExceptionCrashInfo::default(),
  };
  CRASH_STATE.store(state_ptr, Ordering::Release);
}

pub(crate) fn record_nsexception(name: &str, reason: Option<&str>, return_addresses: &[u64]) {
  let state_ptr = CRASH_STATE.load(Ordering::Acquire);
  if state_ptr.is_null() {
    return;
  }

  let state = unsafe { &mut *state_ptr };
  state.header.state = STATE_CRASHED;
  state.header.kind = CrashStateKind::NSException;
  state.timestamp_secs = current_timestamp_secs();
  state.pid = std::process::id();
  write_string(name, &mut state.nsexception.name);
  if let Some(reason) = reason {
    write_string(reason, &mut state.nsexception.reason);
  } else {
    state.nsexception.reason.fill(0);
  }
  state.nsexception.call_stack.frame_count =
    return_addresses.len().min(MAX_NS_EXCEPTION_CALL_STACK_FRAMES) as u16;
  state.nsexception.call_stack.return_addresses.fill(0);
  let copy_len = usize::from(state.nsexception.call_stack.frame_count);
  state.nsexception.call_stack.return_addresses[..copy_len]
    .copy_from_slice(&return_addresses[..copy_len]);
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
  use super::{
    previous_crash_state,
    prime_shared_state,
    record_nsexception,
    CrashState,
    CrashStateHeader,
    CrashStateKind,
    PreviousCrashDetails,
    CRASH_STATE,
    MAX_NS_EXCEPTION_CALL_STACK_FRAMES,
    MAGIC,
    STATE_CRASHED,
    STATE_RUNNING,
    NS_EXCEPTION_NAME_CAPACITY,
    NS_EXCEPTION_REASON_CAPACITY,
    NSExceptionCallStack,
    NSExceptionCrashInfo,
    VERSION,
  };
  use std::sync::atomic::Ordering;

  #[test]
  fn previous_state_reads_nsexception_payload() {
    let mut name = [0; NS_EXCEPTION_NAME_CAPACITY];
    name[.. 12].copy_from_slice(b"NSException\0");
    let mut reason = [0; NS_EXCEPTION_REASON_CAPACITY];
    reason[.. 12].copy_from_slice(b"bad reason!\0");
    let mut return_addresses = [0; MAX_NS_EXCEPTION_CALL_STACK_FRAMES];
    return_addresses[..3].copy_from_slice(&[1, 2, 3]);

    let state = CrashState {
      header: CrashStateHeader {
        magic: MAGIC,
        version: VERSION,
        state: STATE_CRASHED,
        kind: CrashStateKind::NSException,
      },
      timestamp_secs: 12,
      pid: 1,
      nsexception: NSExceptionCrashInfo {
        name,
        reason,
        call_stack: NSExceptionCallStack {
          frame_count: 3,
          return_addresses,
        },
      },
    };

    let previous_state = previous_crash_state(&state);

    assert!(previous_state.did_crash);
    assert!(matches!(
      previous_state.details,
      PreviousCrashDetails::NSException(_)
    ));
  }

  #[test]
  fn prime_shared_state_resets_state() {
    let mut state = CrashState::default();

    unsafe {
      prime_shared_state(&mut state);
    }

    assert_eq!(state.header.magic, MAGIC);
    assert_eq!(state.header.version, VERSION);
    assert_eq!(state.header.state, STATE_RUNNING);
  }

  #[test]
  fn record_nsexception_updates_shared_state() {
    let mut state = CrashState::default();
    unsafe {
      prime_shared_state(&mut state);
    }

    record_nsexception("NSException", Some("bad reason"), &[10, 11, 12]);

    let state_ptr = CRASH_STATE.load(Ordering::Acquire);
    let state = unsafe { &*state_ptr };
    assert_eq!(state.header.state, STATE_CRASHED);
    assert_eq!(state.header.kind, CrashStateKind::NSException);
    assert_eq!(&state.nsexception.name[.. 12], b"NSException\0");
    assert_eq!(state.nsexception.call_stack.frame_count, 3);
    assert_eq!(
      &state.nsexception.call_stack.return_addresses[..3],
      &[10, 11, 12]
    );
  }
}
