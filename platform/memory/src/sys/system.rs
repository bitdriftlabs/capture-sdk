// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./system_test.rs"]
mod tests;

use super::mach::{KERN_SUCCESS, count_of, task_self};
use libc::{c_void, integer_t, natural_t};
use std::ffi::CString;

/// From `<mach/task_info.h>`.
const TASK_DYLD_INFO: natural_t = 17;

/// The first `dyld_all_image_infos` version with `sharedCacheBaseAddress`.
const SHARED_CACHE_BASE_ADDRESS_VERSION: u32 = 15;

//
// TaskDyldInfo
//

/// `struct task_dyld_info` from `<mach/task_info.h>`, which packs to 4 bytes.
#[repr(C, packed(4))]
#[derive(Default)]
struct TaskDyldInfo {
  address: u64,
  size: u64,
  format: integer_t,
}

//
// DyldAllImageInfos
//

/// The prefix of `struct dyld_all_image_infos` from `<mach-o/dyld_images.h>`, up to
/// `sharedCacheBaseAddress`.
#[repr(C)]
struct DyldAllImageInfos {
  version: u32,
  info_array_count: u32,
  info_array: *const c_void,
  notification: *const c_void,
  process_detached_from_shared_region: bool,
  lib_system_initialized: bool,
  /// `dyldImageLoadAddress` through `sharedCacheSlide`.
  words: [usize; 16],
  shared_cache_uuid: [u8; 16],
  shared_cache_base_address: usize,
}

/// dyld's own table, which lives for the life of the process.
fn all_image_infos() -> Option<&'static DyldAllImageInfos> {
  let mut info = TaskDyldInfo::default();
  let mut count = count_of::<TaskDyldInfo>();
  // SAFETY: `info` is a writable buffer of `count` naturals, and the kernel writes at most that.
  let status = unsafe {
    libc::task_info(
      task_self(),
      TASK_DYLD_INFO,
      (&raw mut info).cast::<integer_t>(),
      &raw mut count,
    )
  };
  if status != KERN_SUCCESS {
    return None;
  }
  let address = usize::try_from(info.address).ok()?;
  // SAFETY: the kernel reports where dyld keeps the table in this process; NULL gives None.
  let infos = unsafe { (address as *const DyldAllImageInfos).as_ref()? };
  (infos.version >= SHARED_CACHE_BASE_ADDRESS_VERSION).then_some(infos)
}

/// A string sysctl from `<sys/sysctl.h>`, such as hw.machine, or None if it does not exist.
pub fn sysctl_string(name: &str) -> Option<String> {
  let name = CString::new(name).ok()?;
  let mut length = 0;
  // SAFETY: a NULL buffer asks for the length only.
  let status = unsafe {
    libc::sysctlbyname(
      name.as_ptr(),
      std::ptr::null_mut(),
      &raw mut length,
      std::ptr::null_mut(),
      0,
    )
  };
  if status != 0 || length == 0 {
    return None;
  }
  let mut buffer = vec![0u8; length];
  // SAFETY: `buffer` holds `length` bytes, the size the kernel asked for.
  let status = unsafe {
    libc::sysctlbyname(
      name.as_ptr(),
      buffer.as_mut_ptr().cast::<c_void>(),
      &raw mut length,
      std::ptr::null_mut(),
      0,
    )
  };
  if status != 0 {
    return None;
  }
  buffer.truncate(length);
  let end = buffer
    .iter()
    .position(|byte| *byte == 0)
    .unwrap_or(buffer.len());
  buffer.truncate(end);
  String::from_utf8(buffer).ok()
}

/// The UUID of the dyld shared cache this process maps, or None if it maps none.
pub fn shared_cache_uuid() -> Option<[u8; 16]> {
  let uuid = all_image_infos()?.shared_cache_uuid;
  (uuid != [0; 16]).then_some(uuid)
}

/// The address the dyld shared cache is mapped at, or None if the process maps none.
pub fn shared_cache_start() -> Option<usize> {
  let start = all_image_infos()?.shared_cache_base_address;
  (start != 0).then_some(start)
}
