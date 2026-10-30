// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// `vm_region_recurse_64`: the regions of this task's VM map, descending into submaps.

use super::mach::{KERN_SUCCESS, count_of, task_self};
use libc::{
  c_int,
  kern_return_t,
  mach_msg_type_number_t,
  mach_port_t,
  natural_t,
  vm_address_t,
  vm_size_t,
};

pub const VM_PROT_READ: c_int = 0x1;
pub const VM_PROT_WRITE: c_int = 0x2;

pub const SM_COW: u8 = 1;
pub const SM_SHARED: u8 = 3;
pub const SM_TRUESHARED: u8 = 5;
pub const SM_SHARED_ALIASED: u8 = 7;

unsafe extern "C" {
  fn vm_region_recurse_64(
    target_task: mach_port_t,
    address: *mut vm_address_t,
    size: *mut vm_size_t,
    nesting_depth: *mut natural_t,
    info: *mut c_int,
    info_count: *mut mach_msg_type_number_t,
  ) -> kern_return_t;
}

//
// RegionShortInfo
//

/// `struct vm_region_submap_short_info_64`, packed to 4 bytes.
#[repr(C, packed(4))]
#[derive(Clone, Copy, Default)]
pub struct RegionShortInfo {
  pub protection: c_int,
  pub max_protection: c_int,
  pub inheritance: u32,
  pub offset: u64,
  pub user_tag: u32,
  pub ref_count: u32,
  pub shadow_depth: u16,
  pub external_pager: u8,
  pub share_mode: u8,
  pub is_submap: c_int,
  pub behavior: c_int,
  pub object_id: u32,
  pub user_wired_count: u16,
  pub flags: u16,
}

//
// RegionInfo
//

/// `struct vm_region_submap_info_64`, packed to 4 bytes.
#[repr(C, packed(4))]
#[derive(Clone, Copy, Default)]
pub struct RegionInfo {
  pub protection: c_int,
  pub max_protection: c_int,
  pub inheritance: u32,
  pub offset: u64,
  pub user_tag: u32,
  pub pages_resident: u32,
  pub pages_shared_now_private: u32,
  pub pages_swapped_out: u32,
  pub pages_dirtied: u32,
  pub ref_count: u32,
  pub shadow_depth: u16,
  pub external_pager: u8,
  pub share_mode: u8,
  pub is_submap: c_int,
  pub behavior: c_int,
  pub object_id: u32,
  pub user_wired_count: u16,
  pub flags: u16,
  pub pages_reusable: u32,
  pub object_id_full: u64,
}

//
// Region
//

/// A region as reported by `vm_region_recurse_64`: the kernel moves the address forward to the
/// next mapped region and reports the depth it found it at.
pub struct Region<T> {
  pub address: usize,
  pub size: usize,
  pub depth: u32,
  pub info: T,
}

fn region<T: Default>(address: usize, depth: u32) -> Result<Region<T>, kern_return_t> {
  let mut address = address as vm_address_t;
  let mut size: vm_size_t = 0;
  let mut depth = depth as natural_t;
  let mut info = T::default();
  let mut count = count_of::<T>();
  // SAFETY: every pointer refers to a local of the type and size the kernel expects for `count`.
  let status = unsafe {
    vm_region_recurse_64(
      task_self(),
      &raw mut address,
      &raw mut size,
      &raw mut depth,
      (&raw mut info).cast::<c_int>(),
      &raw mut count,
    )
  };
  if status != KERN_SUCCESS {
    return Err(status);
  }
  Ok(Region {
    address: address as usize,
    size: size as usize,
    depth,
    info,
  })
}

/// The first region at or after `address`, without page counts.
pub fn region_short_info(
  address: usize,
  depth: u32,
) -> Result<Region<RegionShortInfo>, kern_return_t> {
  region(address, depth)
}

/// The first region at or after `address`, with page counts.
pub fn region_info(address: usize, depth: u32) -> Result<Region<RegionInfo>, kern_return_t> {
  region(address, depth)
}
