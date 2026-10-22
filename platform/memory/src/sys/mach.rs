// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use libc::{
  kern_return_t,
  mach_msg_type_number_t,
  mach_port_t,
  natural_t,
  vm_address_t,
  vm_size_t,
};

pub const KERN_SUCCESS: kern_return_t = 0;
pub const KERN_INVALID_ADDRESS: kern_return_t = 1;

const CLOCK_UPTIME_RAW: libc::clockid_t = 8;

unsafe extern "C" {
  fn vm_read_overwrite(
    target_task: mach_port_t,
    address: vm_address_t,
    size: vm_size_t,
    data: vm_address_t,
    out_size: *mut vm_size_t,
  ) -> kern_return_t;

  fn clock_gettime_nsec_np(clock_id: libc::clockid_t) -> u64;

  static vm_kernel_page_size: vm_size_t;
  static mach_task_self_: mach_port_t;

  #[cfg(target_os = "ios")]
  fn os_proc_available_memory() -> usize;
}

pub fn task_self() -> mach_port_t {
  // SAFETY: reads the task port the runtime initializes before main.
  unsafe { mach_task_self_ }
}

/// The size of a kernel info struct in naturals, as the info calls count it.
#[allow(clippy::cast_possible_truncation)]
pub const fn count_of<T>() -> mach_msg_type_number_t {
  (size_of::<T>() / size_of::<natural_t>()) as mach_msg_type_number_t
}

/// Copies `destination.len()` bytes from `address`, failing instead of faulting when any of them
/// is unmapped.
pub fn read_memory(address: usize, destination: &mut [u8]) -> bool {
  if address == 0 || destination.is_empty() {
    return false;
  }
  let mut copied: vm_size_t = 0;
  // SAFETY: the kernel validates the source range and writes at most `destination.len()` bytes
  // into it.
  let status = unsafe {
    vm_read_overwrite(
      task_self(),
      address as vm_address_t,
      destination.len() as vm_size_t,
      destination.as_mut_ptr() as vm_address_t,
      &raw mut copied,
    )
  };
  status == KERN_SUCCESS && copied as usize == destination.len()
}

pub fn now_ns() -> u64 {
  // SAFETY: no pointers involved.
  unsafe { clock_gettime_nsec_np(CLOCK_UPTIME_RAW) }
}

pub fn kernel_page_size() -> u64 {
  // SAFETY: a constant the kernel publishes before main.
  unsafe { vm_kernel_page_size as u64 }
}

pub fn page_size() -> usize {
  // SAFETY: a constant the kernel publishes before main.
  unsafe { libc::vm_page_size }
}

#[cfg(target_os = "ios")]
pub fn available_memory() -> u64 {
  // SAFETY: no pointers involved.
  unsafe { os_proc_available_memory() as u64 }
}

#[cfg(not(target_os = "ios"))]
pub const fn available_memory() -> u64 {
  0
}
