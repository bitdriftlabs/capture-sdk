// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use libc::{c_char, intptr_t};
use std::ffi::CStr;

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

pub fn image_path(index: u32) -> Option<String> {
  // SAFETY: dyld returns NULL or a NUL-terminated path it owns.
  let path = unsafe { _dyld_get_image_name(index) };
  if path.is_null() {
    return None;
  }
  // SAFETY: checked for NULL above.
  Some(
    unsafe { CStr::from_ptr(path) }
      .to_string_lossy()
      .into_owned(),
  )
}
