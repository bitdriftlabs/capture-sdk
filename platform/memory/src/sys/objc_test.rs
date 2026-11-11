// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;
use crate::test_support::memory_lock;
use std::collections::HashSet;

#[test]
fn image_classes_match_the_runtime_except_swift_generic_instances_and_test_classes() {
  let _lock = memory_lock();
  // SAFETY: loads a system framework, whose classes come from the shared cache.
  let handle = unsafe {
    libc::dlopen(
      c"/System/Library/Frameworks/Foundation.framework/Foundation".as_ptr(),
      libc::RTLD_NOW,
    )
  };
  assert!(!handle.is_null());

  let images: HashSet<usize> = image_classes().unwrap().iter().copied().collect();
  let runtime = Class::classes();
  let registered: HashSet<usize> = runtime
    .iter()
    .map(|class| std::ptr::from_ref::<Class>(class) as usize)
    .collect();

  assert!(images.len() > 5_000, "{}", images.len());
  assert!(images.is_subset(&registered));
  for class in runtime.iter() {
    if !images.contains(&(std::ptr::from_ref::<Class>(class) as usize)) {
      let name = class_name(class).to_string_lossy();
      assert!(
        (name.starts_with("_Tt") && name.contains('G')) || name.starts_with("BdMemoryDump"),
        "{name}"
      );
    }
  }
}

#[test]
fn known_classes_come_from_the_images() {
  let _lock = memory_lock();
  let known: HashSet<usize> = known_classes().unwrap().iter().copied().collect();
  let images: HashSet<usize> = image_classes().unwrap().iter().copied().collect();
  assert_eq!(known, images);
}

#[test]
fn runtime_classes_include_the_root_class_an_empty_list_misses() {
  assert!(!includes_root_class(&[]));
  assert!(includes_root_class(&runtime_classes().unwrap()));
}

#[test]
fn class_lists_leave_out_null_entries() {
  let list: Vec<usize> = (0 .. 1_500)
    .map(|index| if index % 3 == 0 { 0 } else { index })
    .collect();
  let mut classes = MappedVec::with_capacity(list.len()).unwrap();
  copy_class_list(
    list.as_ptr() as usize,
    list.len() * size_of::<usize>(),
    &mut classes,
  );
  let expected: Vec<usize> = list.iter().copied().filter(|class| *class != 0).collect();
  assert_eq!(&*classes, expected.as_slice());
}

#[test]
fn an_unreadable_class_list_adds_nothing() {
  let mut classes = MappedVec::with_capacity(8).unwrap();
  copy_class_list(0, size_of::<[usize; 8]>(), &mut classes);
  assert!(classes.is_empty());
}
