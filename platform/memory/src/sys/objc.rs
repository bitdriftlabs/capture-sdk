// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use objc::runtime::{Class, class_getName};
use std::ffi::CStr;

//
// RuntimeClass
//

/// A class the runtime lists.
pub struct RuntimeClass {
  pub pointer: usize,
  pub instance_size: usize,
  /// Owned by the runtime for the life of the process, so listing classes allocates no names.
  pub name: &'static CStr,
}

/// Every class the runtime has registered. Realizes them all, which allocates.
pub fn registered_classes() -> Vec<RuntimeClass> {
  Class::classes()
    .iter()
    .map(|class| RuntimeClass {
      pointer: std::ptr::from_ref::<Class>(class) as usize,
      instance_size: class.instance_size(),
      name: class_name(class),
    })
    .collect()
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
