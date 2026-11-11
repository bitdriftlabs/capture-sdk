// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use crate::safe_read;
use libc::{c_char, intptr_t};

/// `PATH_MAX` from `<sys/syslimits.h>`.
const PATH_CAPACITY: usize = 1024;

// From `<mach-o/dyld.h>`.
unsafe extern "C" {
  fn _dyld_image_count() -> u32;
  fn _dyld_get_image_header(image_index: u32) -> *const libc::c_void;
  fn _dyld_get_image_vmaddr_slide(image_index: u32) -> intptr_t;
  fn _dyld_get_image_name(image_index: u32) -> *const c_char;
}

pub fn image_count() -> u32 {
  // SAFETY: no pointers involved.
  unsafe { _dyld_image_count() }
}

/// The address of an image's Mach-O header and its slide, or None once it is unloaded.
pub fn image_header(index: u32) -> Option<(usize, usize)> {
  // SAFETY: dyld returns NULL for an index past the loaded images.
  let header = unsafe { _dyld_get_image_header(index) };
  if header.is_null() {
    return None;
  }
  // SAFETY: as above.
  let slide = unsafe { _dyld_get_image_vmaddr_slide(index) };
  Some((header as usize, slide.cast_unsigned()))
}

/// Copied with a safe read: the path belongs to dyld and goes away if the image is unloaded.
pub fn image_path(index: u32) -> Option<String> {
  // SAFETY: dyld returns NULL or a NUL-terminated path it owns.
  let path = unsafe { _dyld_get_image_name(index) };
  if path.is_null() {
    return None;
  }
  let path = safe_read::read_string(path as usize, PATH_CAPACITY)?;
  Some(String::from_utf8_lossy(&path).into_owned())
}
