// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./apple_test.rs"]
mod tests;

use crate::memory::MemoryReader;

#[link(name = "System")]
unsafe extern "C" {
  static mach_task_self_: u32;

  fn mach_vm_read_overwrite(
    task: u32,
    address: u64,
    size: u64,
    destination: u64,
    copied: *mut u64,
  ) -> i32;
}

//
// TaskMemory
//

/// Reads the current process through Mach instead of dereferencing possibly unmapped memory.
/// The destination must already exist; Mach validates the source and copies into the caller's
/// buffer. This reader does not suspend threads or make a running thread's stack coherent.
pub struct TaskMemory;

impl MemoryReader for TaskMemory {
  fn read(&self, address: u64, destination: &mut [u8]) -> bool {
    if destination.is_empty() {
      return true;
    }
    let size = destination.len() as u64;
    if address == 0 || address.checked_add(size).is_none() {
      return false;
    }
    let mut copied = 0;
    // SAFETY: the kernel checks source accessibility. The Rust slice owns `size` writable bytes,
    // and `copied` is a live output parameter. The runtime initializes mach_task_self_ before main.
    let result = unsafe {
      mach_vm_read_overwrite(
        mach_task_self_,
        address,
        size,
        destination.as_mut_ptr() as u64,
        &raw mut copied,
      )
    };
    result == 0 && copied == size
  }
}
