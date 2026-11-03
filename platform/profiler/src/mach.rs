// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use crate::CaptureError;
use bd_unwind::aarch64::Registers;
use bd_unwind::memory::AddressRange;
use std::mem::size_of;
use std::slice;

#[cfg(test)]
#[path = "./mach_test.rs"]
mod tests;

// Mach flavor IDs and word counts mirror Apple's thread_info.h, arm/thread_status.h and
// vm_region.h. A flavor selects the response layout; its count is measured in 32-bit words.
const ARM_THREAD_STATE64: i32 = 6;
const ARM_THREAD_STATE64_COUNT: u32 = 68;
#[cfg(test)]
const THREAD_BASIC_INFO: u32 = 3;
#[cfg(test)]
const THREAD_BASIC_INFO_COUNT: u32 = 10;
const THREAD_IDENTIFIER_INFO: u32 = 4;
const THREAD_IDENTIFIER_INFO_COUNT: u32 = 6;
const THREAD_EXTENDED_INFO: u32 = 5;
const THREAD_EXTENDED_INFO_COUNT: u32 = 28;
const VM_REGION_BASIC_INFO_64: i32 = 9;
const VM_REGION_BASIC_INFO_COUNT_64: u32 = 9;
const VM_PROT_READ: i32 = 1;

#[link(name = "System")]
unsafe extern "C" {
  pub(super) static mach_task_self_: u32;
  fn mach_thread_self() -> u32;
  fn mach_port_deallocate(task: u32, name: u32) -> i32;
  fn task_threads(task: u32, threads: *mut *mut u32, count: *mut u32) -> i32;
  fn mach_vm_deallocate(task: u32, address: u64, size: u64) -> i32;
  fn thread_suspend(thread: u32) -> i32;
  pub(super) fn thread_resume(thread: u32) -> i32;
  fn thread_get_state(thread: u32, flavor: i32, state: *mut u32, count: *mut u32) -> i32;
  fn thread_info(thread: u32, flavor: u32, info: *mut i32, count: *mut u32) -> i32;
  fn mach_vm_region(
    task: u32,
    address: *mut u64,
    size: *mut u64,
    flavor: i32,
    info: *mut i32,
    count: *mut u32,
    object: *mut u32,
  ) -> i32;
  pub(super) fn mach_absolute_time() -> u64;
  pub(super) fn mach_timebase_info(info: *mut Timebase) -> i32;
  pub(super) fn _dyld_image_count() -> u32;
  pub(super) fn _dyld_get_image_header(index: u32) -> *const u8;
  pub(super) fn getpid() -> i32;
}

//
// Timebase
//

#[repr(C)]
pub(super) struct Timebase {
  pub(super) numerator: u32,
  pub(super) denominator: u32,
}

impl Timebase {
  pub(super) fn nanos(&self, ticks: u64) -> u64 {
    u64::try_from(u128::from(ticks) * u128::from(self.numerator) / u128::from(self.denominator))
      .unwrap_or(u64::MAX)
  }
}

//
// Threads
//

pub(super) struct Threads {
  ports: *mut u32,
  count: u32,
  pub(super) current: u32,
}

impl Threads {
  pub(super) fn new() -> Result<Self, CaptureError> {
    let mut ports = std::ptr::null_mut();
    let mut count = 0;
    // SAFETY: all outputs are live. Mach supplies count port rights and a VM-allocated array.
    if unsafe { task_threads(mach_task_self_, &raw mut ports, &raw mut count) } != 0 {
      return Err(CaptureError::ThreadEnumerationFailed);
    }
    Ok(Self {
      ports,
      count,
      current: unsafe { mach_thread_self() },
    })
  }

  pub(super) const fn ports(&self) -> &[u32] {
    if self.count == 0 {
      return &[];
    }
    // SAFETY: task_threads returned this array, retained until Drop.
    unsafe { slice::from_raw_parts(self.ports, self.count as usize) }
  }
}

impl Drop for Threads {
  fn drop(&mut self) {
    // SAFETY: these are the rights and array owned by this enumeration, including current's extra
    // send right. No threads are suspended when this owner is destroyed.
    unsafe {
      for port in self.ports() {
        mach_port_deallocate(mach_task_self_, *port);
      }
      mach_port_deallocate(mach_task_self_, self.current);
      if self.count != 0 {
        mach_vm_deallocate(
          mach_task_self_,
          self.ports as u64,
          u64::from(self.count) * size_of::<u32>() as u64,
        );
      }
    }
  }
}

//
// Suspended
//

pub(super) struct Suspended {
  port: u32,
  active: bool,
}

impl Suspended {
  pub(super) fn new(port: u32) -> Option<Self> {
    // SAFETY: port is retained by Threads; the capture thread is excluded by the caller.
    if unsafe { thread_suspend(port) } == 0 {
      Some(Self { port, active: true })
    } else {
      None
    }
  }

  pub(super) fn resume(mut self) -> Result<(), CaptureError> {
    // SAFETY: balance exactly the suspension performed by new, preserving existing suspend counts.
    if unsafe { thread_resume(self.port) } != 0 {
      return Err(CaptureError::ResumeFailed);
    }
    self.active = false;
    Ok(())
  }
}

impl Drop for Suspended {
  fn drop(&mut self) {
    if self.active {
      // SAFETY: exceptional cleanup retries the suspension owned by this guard.
      unsafe {
        thread_resume(self.port);
      }
    }
  }
}

// ARM_THREAD_STATE64 consists of 29 integer registers, FP, LR, SP, PC, CPSR and flags.
// This mirrors the plain-arm64 accessors in Apple's mach/arm/_structs.h; arm64e compilation is
// not supported here. The unwinder strips PAC from instruction addresses using XPACLRI.
#[repr(C)]
#[derive(Default)]
struct ThreadState {
  x: [u64; 29],
  fp: u64,
  lr: u64,
  sp: u64,
  pc: u64,
  cpsr: u32,
  flags: u32,
}

pub(super) fn registers(port: u32) -> Option<Registers> {
  let mut state = ThreadState::default();
  let mut count = ARM_THREAD_STATE64_COUNT;
  // SAFETY: state provides 68 writable u32 words, with C layout and sufficient alignment.
  let status = unsafe {
    thread_get_state(
      port,
      ARM_THREAD_STATE64,
      (&raw mut state).cast(),
      &raw mut count,
    )
  };
  if status != 0 || count != ARM_THREAD_STATE64_COUNT {
    return None;
  }
  Some(Registers {
    x: state.x,
    fp: state.fp,
    lr: state.lr,
    sp: state.sp,
    pc: state.pc,
  })
}

pub(super) fn stack_region(sp: u64) -> Option<AddressRange> {
  let mut start = sp;
  let mut size = 0;
  let mut info = [0_i32; VM_REGION_BASIC_INFO_COUNT_64 as usize];
  let mut count = VM_REGION_BASIC_INFO_COUNT_64;
  let mut object = 0;
  // SAFETY: VM_REGION_BASIC_INFO_64 uses nine 32-bit words; all output storage is live.
  let result = unsafe {
    mach_vm_region(
      mach_task_self_,
      &raw mut start,
      &raw mut size,
      VM_REGION_BASIC_INFO_64,
      info.as_mut_ptr(),
      &raw mut count,
      &raw mut object,
    )
  };
  if object != 0 {
    unsafe {
      mach_port_deallocate(mach_task_self_, object);
    }
  }
  let region = AddressRange::new(start, size)?;
  // mach_vm_region may return the next mapping when sp is in a hole.
  (result == 0
    && count == VM_REGION_BASIC_INFO_COUNT_64
    && info[0] & VM_PROT_READ != 0
    && region.contains(sp, 1))
  .then_some(region)
}

pub(super) fn metadata(port: u32) -> (u64, String) {
  let mut identity = [0_u64; 3];
  let mut count = THREAD_IDENTIFIER_INFO_COUNT;
  // SAFETY: THREAD_IDENTIFIER_INFO is three u64 values, exposed as six integer words.
  let status = unsafe {
    thread_info(
      port,
      THREAD_IDENTIFIER_INFO,
      identity.as_mut_ptr().cast(),
      &raw mut count,
    )
  };
  let native_id = if status == 0 && count == THREAD_IDENTIFIER_INFO_COUNT {
    identity[0]
  } else {
    u64::from(port)
  };
  let mut info = [0_u64; 14];
  count = THREAD_EXTENDED_INFO_COUNT;
  // SAFETY: THREAD_EXTENDED_INFO is 112 bytes; the name occupies the final 64 bytes.
  let status = unsafe {
    thread_info(
      port,
      THREAD_EXTENDED_INFO,
      info.as_mut_ptr().cast(),
      &raw mut count,
    )
  };
  let name = if status == 0 && count == THREAD_EXTENDED_INFO_COUNT {
    let bytes = unsafe { slice::from_raw_parts(info.as_ptr().cast::<u8>().add(48), 64) };
    let end = bytes
      .iter()
      .position(|byte| *byte == 0)
      .unwrap_or(bytes.len());
    String::from_utf8_lossy(&bytes[.. end]).into_owned()
  } else {
    String::new()
  };
  (native_id, name)
}
