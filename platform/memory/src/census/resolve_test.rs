// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::*;
use crate::test_support::memory_lock;
use objc::class;
use objc::declare::ClassDecl;
use objc::runtime::Class;
use std::sync::OnceLock;

fn with_resolver(test: impl FnOnce(&Resolver<'_>)) {
  let classes = ClassTable::new().unwrap();
  let mut ranges = ReadableRanges::new().unwrap();
  ranges.survey();
  let images = ImageTable::survey();
  test(&Resolver {
    classes: &classes,
    ranges: &ranges,
    images: Some(&images),
  });
}

fn address_of(class: &Class) -> usize {
  std::ptr::from_ref::<Class>(class) as usize
}

/// A class registered at runtime, which no image lists.
fn unlisted_class() -> &'static Class {
  static CLASS: OnceLock<&'static Class> = OnceLock::new();
  CLASS.get_or_init(|| {
    ClassDecl::new("BdMemoryDumpUnlistedClass", class!(NSObject))
      .unwrap()
      .register()
  })
}

#[test]
fn a_class_no_image_lists_is_named() {
  let _lock = memory_lock();
  let class = address_of(unlisted_class());
  with_resolver(|resolver| {
    assert_eq!(resolver.classes.find(class), None);
    assert_eq!(
      resolver.class_name(class).as_deref(),
      Some("BdMemoryDumpUnlistedClass")
    );
  });
}

#[test]
fn an_array_of_class_pointers_is_not_a_class() {
  // The root class as isa closes the isa chain and a known class as superclass ends the superclass
  // chain, but the runtime would read garbage as its class data.
  let _lock = memory_lock();
  let root = address_of(class!(NSObject));
  let fake = Box::new([root, root, 0, 0, 0, 0]);
  let fake = fake.as_ptr() as usize;
  with_resolver(|resolver| {
    assert!(resolver.isa_chain_is_consistent(fake));
    assert!(resolver.superclass_chain_reaches_known(fake));
    assert_eq!(resolver.class_name(fake), None);
  });
}

#[test]
fn a_system_vtable_is_named() {
  let _lock = memory_lock();
  // SAFETY: libc++abi is in the shared cache and stays loaded.
  let vtable = unsafe {
    libc::dlopen(c"/usr/lib/libc++abi.dylib".as_ptr(), libc::RTLD_NOW);
    libc::dlsym(libc::RTLD_DEFAULT, c"_ZTVSt9bad_alloc".as_ptr())
  } as usize;
  assert_ne!(vtable, 0);
  // Objects point past the offset to top and the `type_info` slot.
  let address_point = vtable + 2 * size_of::<usize>();
  with_resolver(|resolver| {
    assert_eq!(
      resolver.cxx_name(address_point).as_deref(),
      Some("St9bad_alloc")
    );
  });
}

#[test]
fn rtti_built_from_heap_data_is_not_named() {
  let _lock = memory_lock();
  let name = Box::new(*b"9SecretKey\0");
  let type_info = Box::new([0, name.as_ptr() as usize]);
  let vtable = Box::new([type_info.as_ptr() as usize, 0]);
  let address_point = vtable.as_ptr() as usize + size_of::<usize>();
  with_resolver(|resolver| {
    assert_eq!(resolver.cxx_name(address_point), None);
  });
}

#[test]
fn itanium_names_are_cxx_type_names() {
  assert!(is_cxx_type_name(b"N7WebCore4NodeE"));
  assert!(is_cxx_type_name(b"9MyWidget"));
  assert!(is_cxx_type_name(b"St9exception"));
  assert!(is_cxx_type_name(b"*N3foo3BarE"));
}

#[test]
fn swift_names_are_not_cxx_type_names() {
  assert!(!is_cxx_type_name(b"_TtP7SwiftUI4View_"));
  assert!(!is_cxx_type_name(b"_TtC8MyModule6MyType"));
  assert!(!is_cxx_type_name(b"_T0So8NSObjectC"));
  assert!(!is_cxx_type_name(b"_$s8MyModule6MyTypeC"));
  assert!(!is_cxx_type_name(b"$s8MyModule6MyTypeC"));
}

#[test]
fn short_or_odd_names_are_not_cxx_type_names() {
  assert!(!is_cxx_type_name(b""));
  assert!(!is_cxx_type_name(b"N"));
  assert!(!is_cxx_type_name(b"-foo"));
}
