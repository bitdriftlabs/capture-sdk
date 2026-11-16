// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use libc::{kern_return_t, mach_msg_type_number_t, mach_port_t, natural_t};

pub const KERN_SUCCESS: kern_return_t = 0;

unsafe extern "C" {
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

#[cfg(target_os = "ios")]
pub fn available_memory() -> u64 {
  // SAFETY: no pointers involved.
  unsafe { os_proc_available_memory() as u64 }
}

#[cfg(not(target_os = "ios"))]
pub const fn available_memory() -> u64 {
  0
}
