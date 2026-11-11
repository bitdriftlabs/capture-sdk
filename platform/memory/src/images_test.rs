// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;
use crate::test_support::memory_lock;

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

#[test]
fn segments_do_not_overlap() {
  let images = ImageTable::survey();
  for pair in images.segments.windows(2) {
    assert!(
      pair[0].end <= pair[1].start,
      "{:?} overlaps {:?}",
      images.name(pair[0].image),
      images.name(pair[1].image)
    );
  }
}

#[test]
fn linkedit_and_pagezero_are_matched_by_their_whole_names() {
  assert!(is_left_out(b"__LINKEDIT\0\0\0\0\0\0"));
  assert!(is_left_out(b"__PAGEZERO\0\0\0\0\0\0"));
  assert!(!is_left_out(b"__LINKEDIT_X\0\0\0\0"));
  assert!(!is_left_out(b"__DATA\0\0\0\0\0\0\0\0\0\0"));
}

/// The offset of `filesize` in `struct segment_command_64`, which the image table ignores.
const SEGMENT_FILESIZE: usize = 48;

/// A `segment_command_64` with no sections.
fn segment_command(name: &[u8], address: u64, size: u64, file_size: u64) -> Vec<u8> {
  let mut command = vec![0u8; SEGMENT_COMMAND_SIZE];
  command[.. 4].copy_from_slice(&LC_SEGMENT_64.to_ne_bytes());
  command[4 .. 8].copy_from_slice(&u32::try_from(SEGMENT_COMMAND_SIZE).unwrap().to_ne_bytes());
  command[SEGMENT_SEGNAME .. SEGMENT_SEGNAME + name.len()].copy_from_slice(name);
  command[SEGMENT_VMADDR .. SEGMENT_VMADDR + 8].copy_from_slice(&address.to_ne_bytes());
  command[SEGMENT_VMSIZE .. SEGMENT_VMSIZE + 8].copy_from_slice(&size.to_ne_bytes());
  command[SEGMENT_FILESIZE .. SEGMENT_FILESIZE + 8].copy_from_slice(&file_size.to_ne_bytes());
  command
}

#[test]
fn zero_fill_segments_are_kept_and_pagezero_is_left_out() {
  let commands = [
    segment_command(PAGEZERO, 0, 0x1_0000_0000, 0),
    segment_command(b"__TEXT", 0x1_0000_0000, 0x4000, 0x4000),
    segment_command(b"__BSS_ONLY", 0x1_0000_4000, 0x8000, 0),
    segment_command(LINKEDIT, 0x1_0000_c000, 0x4000, 0x4000),
  ]
  .concat();
  let mut image = vec![0u8; HEADER_SIZE];
  image[.. 4].copy_from_slice(&MH_MAGIC_64.to_ne_bytes());
  image[HEADER_NCMDS .. HEADER_NCMDS + 4].copy_from_slice(&4u32.to_ne_bytes());
  image[HEADER_SIZEOFCMDS .. HEADER_SIZEOFCMDS + 4]
    .copy_from_slice(&u32::try_from(commands.len()).unwrap().to_ne_bytes());
  image.extend_from_slice(&commands);

  let parsed = load_commands(image.as_ptr() as usize);
  assert_eq!(
    parsed.segments,
    vec![(0x1_0000_0000, 0x4000), (0x1_0000_4000, 0x8000)]
  );
}

#[test]
fn the_executable_pagezero_belongs_to_no_image() {
  assert_eq!(ImageTable::survey().owner(0x1000), None);
}

#[test]
fn class_lists_lie_in_loaded_images() {
  // The list starts with a class list's address, which the census tests would take for a class.
  let _lock = memory_lock();
  let images = ImageTable::survey();
  let lists = class_lists();
  assert!(!lists.is_empty());
  for (address, size) in lists {
    assert!(images.owner(address).is_some(), "{address:#x}");
    assert_eq!(size % size_of::<usize>(), 0);
  }
}
