// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#[cfg(test)]
#[path = "./objc_test.rs"]
mod tests;

use super::dyld;
use crate::mapped::MappedVec;
use libc::{c_char, c_ulong, c_void};
use objc::runtime::{Class, class_getName};
use std::ffi::CStr;

/// The segments where linkers place `__objc_classlist`.
const CLASS_LIST_SEGMENTS: [&CStr; 3] = [c"__DATA_CONST", c"__DATA", c"__DATA_DIRTY"];
const CLASS_LIST_SECTION: &CStr = c"__objc_classlist";

// From `<mach-o/getsect.h>`.
unsafe extern "C" {
  fn getsectiondata(
    header: *const c_void,
    segment: *const c_char,
    section: *const c_char,
    size: *mut c_ulong,
  ) -> *const usize;
}

/// Classes the census tests declare at runtime, which no image lists.
#[cfg(test)]
pub static TEST_CLASSES: std::sync::Mutex<Vec<usize>> = std::sync::Mutex::new(Vec::new());

/// The `__objc_classlist` sections of the loaded images: arrays of pointers to the classes each
/// image defines.
fn class_lists() -> Vec<&'static [usize]> {
  (0 .. dyld::image_count())
    .filter_map(dyld::image_header)
    .flat_map(|(header, _)| {
      CLASS_LIST_SEGMENTS.iter().filter_map(move |segment| {
        let mut size: c_ulong = 0;
        // SAFETY: `header` is a loaded image's Mach-O header and the names are NUL-terminated.
        let data = unsafe {
          getsectiondata(
            header as *const c_void,
            segment.as_ptr(),
            CLASS_LIST_SECTION.as_ptr(),
            &raw mut size,
          )
        };
        let len = usize::try_from(size).ok()? / size_of::<usize>();
        if data.is_null() || len == 0 {
          return None;
        }
        // SAFETY: the section holds `len` pointers and stays mapped with its image.
        Some(unsafe { std::slice::from_raw_parts(data, len) })
      })
    })
    .collect()
}

fn image_classes() -> Option<MappedVec<usize>> {
  let lists = class_lists();
  #[cfg(test)]
  let test_classes = TEST_CLASSES
    .lock()
    .unwrap_or_else(std::sync::PoisonError::into_inner);
  #[cfg(test)]
  let lists = lists
    .into_iter()
    .chain([test_classes.as_slice()])
    .collect::<Vec<_>>();
  let mut classes = MappedVec::with_capacity(lists.iter().map(|list| list.len()).sum())?;
  for pointer in lists.into_iter().flatten() {
    let _ = classes.push(*pointer);
  }
  Some(classes)
}

/// Lists every class the runtime knows, realizing them all.
fn runtime_classes() -> Option<MappedVec<usize>> {
  let runtime = Class::classes();
  let mut classes = MappedVec::with_capacity(runtime.len())?;
  for class in runtime.iter() {
    let _ = classes.push(std::ptr::from_ref::<Class>(class) as usize);
  }
  Some(classes)
}

fn includes_root_class(classes: &[usize]) -> bool {
  Class::get("NSObject")
    .is_some_and(|root| classes.contains(&(std::ptr::from_ref::<Class>(root) as usize)))
}

/// Every class the loaded images define, read from their class lists without realizing any.
/// Falls back to the runtime's list, which realizes them all, if those lists miss `NSObject`.
/// None if the table cannot be mapped.
pub fn known_classes() -> Option<MappedVec<usize>> {
  let classes = image_classes()?;
  if includes_root_class(&classes) {
    return Some(classes);
  }
  log::warn!("the image class lists miss NSObject, listing the runtime's classes instead");
  runtime_classes()
}

fn class_name(class: &Class) -> &'static CStr {
  // SAFETY: the runtime returns a NUL-terminated string it never frees.
  unsafe { CStr::from_ptr(class_getName(class)) }
}

/// The runtime's name for a class that is not necessarily registered, such as a Swift generic
/// instantiation.
///
/// # Safety
///
/// `pointer` must point at a class: an arbitrary pointer crashes the runtime.
pub unsafe fn validated_class_name(pointer: usize) -> Option<String> {
  // SAFETY: the caller guarantees `pointer` is a class.
  let class = unsafe { &*(pointer as *const Class) };
  let name = class_name(class);
  (!name.is_empty()).then(|| name.to_string_lossy().into_owned())
}
