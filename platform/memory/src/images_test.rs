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
  let address = binary_name as *const () as usize;

  let mut visited = Vec::new();
  images.for_each_overlap(address, address + 1, |image, bytes| {
    visited.push((image, bytes));
  });
  assert_eq!(visited.len(), 1);
  assert_eq!(visited[0].1, 1);
  let name = images.name(visited[0].0).unwrap();
  assert!(!name.contains('/'));
  assert!(name.starts_with("bd_memory_dump"), "{name}");
}

#[test]
fn binary_name_drops_the_directories() {
  assert_eq!(binary_name("/usr/lib/libobjc.A.dylib"), "libobjc.A.dylib");
  assert_eq!(binary_name("Capture"), "Capture");
}
