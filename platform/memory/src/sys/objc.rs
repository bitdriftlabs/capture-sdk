// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#[cfg(test)]
#[path = "./objc_test.rs"]
mod tests;

use crate::mapped::MappedVec;
use crate::{images, safe_read};
use objc::runtime::{Class, class_getName};
use std::ffi::CStr;

/// Class list entries copied per read.
const CLASS_LIST_CHUNK: usize = 512;

/// Classes the census tests declare at runtime, which no image lists.
#[cfg(test)]
pub static TEST_CLASSES: std::sync::Mutex<Vec<usize>> = std::sync::Mutex::new(Vec::new());

fn image_classes() -> Option<MappedVec<usize>> {
  let lists = images::class_lists();
  #[cfg(test)]
  let test_classes = TEST_CLASSES
    .lock()
    .unwrap_or_else(std::sync::PoisonError::into_inner);
  #[cfg(test)]
  let extra = test_classes.len();
  #[cfg(not(test))]
  let extra = 0;
  let capacity = lists
    .iter()
    .map(|(_, size)| size / size_of::<usize>())
    .sum::<usize>()
    + extra;
  let mut classes = MappedVec::with_capacity(capacity)?;
  for (address, size) in lists {
    copy_class_list(address, size, &mut classes);
  }
  #[cfg(test)]
  for class in test_classes.iter() {
    let _ = classes.push(*class);
  }
  Some(classes)
}

/// Copies a class list through safe reads, so an image unloaded meanwhile cuts it short instead of
/// faulting. Leaves out null entries, which the runtime leaves for classes it discards.
fn copy_class_list(address: usize, size: usize, classes: &mut MappedVec<usize>) {
  let mut chunk = [0u8; CLASS_LIST_CHUNK * size_of::<usize>()];
  let size = size - size % size_of::<usize>();
  let mut offset = 0;
  while offset < size {
    let length = (size - offset).min(chunk.len());
    let Some(at) = address.checked_add(offset) else {
      return;
    };
    if !safe_read::read(at, &mut chunk[.. length]) {
      return;
    }
    let (entries, _) = chunk[.. length].as_chunks::<{ size_of::<usize>() }>();
    for class in entries.iter().map(|entry| usize::from_ne_bytes(*entry)) {
      if class != 0 {
        let _ = classes.push(class);
      }
    }
    offset += length;
  }
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
