// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;

#[test]
fn a_function_belongs_to_the_test_binary() {
  let images = ImageTable::survey();
  let image = images.owner(binary_name as *const () as usize).unwrap();
  let name = images.name(image).unwrap();
  assert!(!name.contains('/'));
  assert!(name.starts_with("bd_memory_dump"), "{name}");
}

#[test]
fn offsets_are_relative_to_the_image_header() {
  let images = ImageTable::survey();
  let address = binary_name as *const () as usize;
  let image = images.owner(address).unwrap();
  let offset = images.offset(image, address).unwrap();
  assert!(offset > 0);
  assert!(offset < 1 << 32);
  assert_eq!(images.offset(image, 0), None);
}

#[test]
fn every_image_has_a_uuid() {
  let images = ImageTable::survey();
  for image in 0 .. images.len() {
    let uuid = images.uuid(image).unwrap();
    assert_ne!(uuid, &[0; 16], "{:?}", images.name(image));
  }
}

#[test]
fn null_belongs_to_no_image() {
  assert_eq!(ImageTable::survey().owner(0), None);
}

#[test]
fn overlaps_cover_a_segment_exactly() {
  let images = ImageTable::survey();
  let address = binary_name as *const () as usize;
  let image = images.owner(address).unwrap();

  let mut visited = Vec::new();
  images.for_each_overlap(address, address + 1, |owner, bytes| {
    visited.push((owner, bytes));
  });
  assert_eq!(visited, vec![(image, 1)]);
}

#[test]
fn binary_name_drops_the_directories() {
  assert_eq!(binary_name("/usr/lib/libobjc.A.dylib"), "libobjc.A.dylib");
  assert_eq!(binary_name("Capture"), "Capture");
}
