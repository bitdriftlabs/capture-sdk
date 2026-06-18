// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

pub(crate) const MAGIC: u64 = u64::from_be_bytes(*b"BDCRASH\0");
pub(crate) const VERSION: u32 = 1;
pub(crate) const NS_EXCEPTION_NAME_CAPACITY: usize = 128;
pub(crate) const NS_EXCEPTION_REASON_CAPACITY: usize = 512;
pub(crate) const MAX_NS_EXCEPTION_CALL_STACK_FRAMES: usize = 64;

pub(crate) const RECORD_STATE_EMPTY: u8 = 0;
pub(crate) const RECORD_STATE_WRITING: u8 = 1;
pub(crate) const RECORD_STATE_COMMITTED: u8 = 2;

pub(crate) const CRASH_KIND_NONE: u8 = 0;
pub(crate) const CRASH_KIND_NS_EXCEPTION: u8 = 1;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct CrashRecordHeader {
  pub(crate) magic: u64,
  pub(crate) version: u32,
  pub(crate) record_state: u8,
  pub(crate) crash_kind: u8,
  pub(crate) reserved: [u8; 2],
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct RawNSExceptionCallStack {
  pub(crate) frame_count: u16,
  pub(crate) reserved: [u8; 6],
  pub(crate) return_addresses: [u64; MAX_NS_EXCEPTION_CALL_STACK_FRAMES],
}

impl Default for RawNSExceptionCallStack {
  fn default() -> Self {
    Self {
      frame_count: 0,
      reserved: [0; 6],
      return_addresses: [0; MAX_NS_EXCEPTION_CALL_STACK_FRAMES],
    }
  }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct RawNSExceptionPayload {
  pub(crate) name: [u8; NS_EXCEPTION_NAME_CAPACITY],
  pub(crate) reason: [u8; NS_EXCEPTION_REASON_CAPACITY],
  pub(crate) call_stack: RawNSExceptionCallStack,
}

impl Default for RawNSExceptionPayload {
  fn default() -> Self {
    Self {
      name: [0; NS_EXCEPTION_NAME_CAPACITY],
      reason: [0; NS_EXCEPTION_REASON_CAPACITY],
      call_stack: RawNSExceptionCallStack::default(),
    }
  }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct CrashRecord {
  pub(crate) header: CrashRecordHeader,
  pub(crate) timestamp_secs: u64,
  pub(crate) pid: u32,
  pub(crate) reserved: [u8; 4],
  pub(crate) nsexception: RawNSExceptionPayload,
}
