// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// `TASK_VM_INFO`: the kernel's accounting of this task.

use super::mach::{KERN_SUCCESS, count_of, task_self};
use libc::{integer_t, natural_t};

const TASK_VM_INFO: natural_t = 22;

//
// TaskVmInfo
//

/// `struct task_vm_info` from `mach/task_info.h`, which packs to 4 bytes.
#[repr(C, packed(4))]
#[derive(Clone, Copy, Default)]
pub struct TaskVmInfo {
  pub virtual_size: u64,
  pub region_count: integer_t,
  pub page_size: integer_t,
  pub resident_size: u64,
  pub resident_size_peak: u64,
  pub device: u64,
  pub device_peak: u64,
  pub internal: u64,
  pub internal_peak: u64,
  pub external: u64,
  pub external_peak: u64,
  pub reusable: u64,
  pub reusable_peak: u64,
  pub purgeable_volatile_pmap: u64,
  pub purgeable_volatile_resident: u64,
  pub purgeable_volatile_virtual: u64,
  pub compressed: u64,
  pub compressed_peak: u64,
  pub compressed_lifetime: u64,
  pub phys_footprint: u64,
  pub min_address: u64,
  pub max_address: u64,
  pub ledger_phys_footprint_peak: i64,
  pub ledger_purgeable_nonvolatile: i64,
  pub ledger_purgeable_novolatile_compressed: i64,
  pub ledger_purgeable_volatile: i64,
  pub ledger_purgeable_volatile_compressed: i64,
  pub ledger_tag_network_nonvolatile: i64,
  pub ledger_tag_network_nonvolatile_compressed: i64,
  pub ledger_tag_network_volatile: i64,
  pub ledger_tag_network_volatile_compressed: i64,
  pub ledger_tag_media_footprint: i64,
  pub ledger_tag_media_footprint_compressed: i64,
  pub ledger_tag_media_nofootprint: i64,
  pub ledger_tag_media_nofootprint_compressed: i64,
  pub ledger_tag_graphics_footprint: i64,
  pub ledger_tag_graphics_footprint_compressed: i64,
  pub ledger_tag_graphics_nofootprint: i64,
  pub ledger_tag_graphics_nofootprint_compressed: i64,
  pub ledger_tag_neural_footprint: i64,
  pub ledger_tag_neural_footprint_compressed: i64,
  pub ledger_tag_neural_nofootprint: i64,
  pub ledger_tag_neural_nofootprint_compressed: i64,
  pub limit_bytes_remaining: u64,
  pub decompressions: integer_t,
  pub ledger_swapins: i64,
  pub ledger_tag_neural_nofootprint_total: i64,
  pub ledger_tag_neural_nofootprint_peak: i64,
}

//
// TaskVmInfoReply
//

/// The struct grew across OS releases, so a field past the kernel's reply was not reported.
pub struct TaskVmInfoReply {
  pub info: TaskVmInfo,
  reply_bytes: usize,
}

impl TaskVmInfoReply {
  /// Whether the reply covers the field at `offset`, which `offset_of!` gives.
  pub const fn reaches<T>(&self, offset: usize) -> bool {
    self.reply_bytes >= offset + size_of::<T>()
  }
}

pub fn task_vm_info() -> Option<TaskVmInfoReply> {
  let mut info = TaskVmInfo::default();
  let mut count = count_of::<TaskVmInfo>();
  // SAFETY: `info` is a writable buffer of `count` naturals, and the kernel writes at most that.
  let status = unsafe {
    libc::task_info(
      task_self(),
      TASK_VM_INFO,
      (&raw mut info).cast::<integer_t>(),
      &raw mut count,
    )
  };
  (status == KERN_SUCCESS).then(|| TaskVmInfoReply {
    info,
    reply_bytes: count as usize * size_of::<natural_t>(),
  })
}
