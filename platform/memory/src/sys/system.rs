// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use libc::c_void;
use std::ffi::CString;

unsafe extern "C" {
  fn _dyld_get_shared_cache_uuid(uuid: *mut u8) -> bool;
  fn _dyld_get_shared_cache_range(length: *mut usize) -> *const c_void;
}

/// A string sysctl, such as hw.machine, or None if it does not exist.
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
  let mut uuid = [0u8; 16];
  // SAFETY: dyld writes exactly 16 bytes.
  unsafe { _dyld_get_shared_cache_uuid(uuid.as_mut_ptr()) }.then_some(uuid)
}

/// The address the dyld shared cache is mapped at, or None if the process maps none.
pub fn shared_cache_start() -> Option<usize> {
  let mut length = 0;
  // SAFETY: dyld writes the length and returns NULL when there is no cache.
  let start = unsafe { _dyld_get_shared_cache_range(&raw mut length) };
  (!start.is_null() && length > 0).then_some(start as usize)
}
