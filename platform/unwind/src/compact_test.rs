// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{CompactUnwind, LookupError};
use crate::memory::{AddressRange, StackMemory};
use crate::test_support::count_allocations;

pub(crate) fn word(bytes: &mut [u8], offset: usize, value: u32) {
  bytes[offset .. offset + 4].copy_from_slice(&value.to_le_bytes());
}

pub(crate) fn table(compressed: bool) -> [u8; 160] {
  let mut bytes = [0; 160];
  for (i, value) in [1, 28, 1, 0, 0, 32, 2].into_iter().enumerate() {
    word(&mut bytes, i * 4, value);
  }
  word(&mut bytes, 28, 0x0400_0000); // Common encoding: ARM64 frame-based.
  word(&mut bytes, 32, 0x100);
  word(&mut bytes, 36, 64);
  word(&mut bytes, 44, 0x200); // Sentinel; not an actual page.
  word(&mut bytes, 64, if compressed { 3 } else { 2 });
  // Two entries, starting twelve bytes into the page.
  word(&mut bytes, 68, (2 << 16) | 0x0c);
  if compressed {
    word(&mut bytes, 72, (1 << 16) | 0x18); // One local encoding.
    word(&mut bytes, 76, 0); // Offset 0, common encoding 0.
    word(&mut bytes, 80, (1 << 24) | 0x80); // Offset 0x80, local encoding 0.
  } else {
    word(&mut bytes, 76, 0x100);
    word(&mut bytes, 80, 0x0400_0000);
    word(&mut bytes, 84, 0x180);
  }
  word(&mut bytes, 88, 0x0200_2000); // Frameless, 32 bytes of stack.
  bytes
}

pub(super) fn lookup(bytes: &[u8], offset: u64) -> Result<Option<u32>, LookupError> {
  CompactUnwind {
    section: AddressRange::new(0x8000, bytes.len() as u64).unwrap(),
    image_base: 0x1_0000_0000,
  }
  .lookup(
    &StackMemory {
      address: 0x8000,
      bytes,
    },
    0x1_0000_0000 + offset,
  )
}

#[test]
fn regular_and_compressed_pages_cover_half_open_ranges() {
  for compressed in [false, true] {
    let bytes = table(compressed);
    for (offset, expected) in [
      (0xff, None),
      (0x100, Some(0x0400_0000)),
      (0x17f, Some(0x0400_0000)),
      (0x180, Some(0x0200_2000)),
      (0x1ff, Some(0x0200_2000)),
      (0x200, None),
      (u64::from(u32::MAX), None),
      (u64::from(u32::MAX) + 1, None),
    ] {
      assert_eq!(lookup(&bytes, offset), Ok(expected));
    }
  }
}

#[test]
fn validates_header_index_and_page_bounds() {
  for (offset, value) in [
    (24, 0),
    (24, 1),
    (24, u32::MAX),
    (20, u32::MAX),
    (4, u32::MAX),
    (8, u32::MAX),
    (36, u32::MAX),
    (36, 0),
    (68, 0),
    (68, (u32::from(u16::MAX)) << 16 | 0x0c),
    (68, (2 << 16) | 0x0ffc),
  ] {
    let mut bytes = table(false);
    word(&mut bytes, offset, value);
    assert_eq!(
      lookup(&bytes, 0x110),
      Err(LookupError::InvalidSection),
      "at {offset}"
    );
  }
  for size in 1 .. 28 {
    assert_eq!(
      lookup(&table(false)[.. size], 0x110),
      Err(LookupError::InvalidSection)
    );
  }
}

#[test]
fn validates_selected_entry_and_encoding_index() {
  let mut bytes = table(false);
  word(&mut bytes, 84, 0x210); // Next entry exceeds its first-level range.
  assert_eq!(lookup(&bytes, 0x110), Err(LookupError::InvalidSection));
  let mut bytes = table(true);
  word(&mut bytes, 80, (2 << 24) | 0x80); // Neither common nor local.
  assert_eq!(lookup(&bytes, 0x190), Err(LookupError::InvalidSection));
  let mut bytes = table(true);
  word(&mut bytes, 72, (1 << 16) | 0x0ffe);
  assert_eq!(lookup(&bytes, 0x190), Err(LookupError::InvalidSection));
}

#[test]
fn distinguishes_version_kind_and_unreadable_memory() {
  let mut bytes = table(false);
  word(&mut bytes, 0, 2);
  assert_eq!(lookup(&bytes, 0x110), Err(LookupError::UnsupportedVersion));
  word(&mut bytes, 0, 1);
  word(&mut bytes, 64, 7);
  assert_eq!(lookup(&bytes, 0x110), Err(LookupError::UnsupportedPage));
  let result = CompactUnwind {
    section: AddressRange::new(0x8000, 160).unwrap(),
    image_base: 0,
  }
  .lookup(
    &StackMemory {
      address: 0x8000,
      bytes: &[],
    },
    0x110,
  );
  assert_eq!(result, Err(LookupError::UnreadableMemory));
}

#[test]
fn lookup_does_not_allocate_or_overflow_runtime_addresses() {
  let bytes = table(true);
  let (result, allocations) = count_allocations(|| lookup(&bytes, 0x190));
  assert_eq!(result, Ok(Some(0x0200_2000)));
  assert_eq!(allocations, 0);
  let base = u64::MAX - bytes.len() as u64;
  assert_eq!(
    CompactUnwind {
      section: AddressRange::new(base, bytes.len() as u64).unwrap(),
      image_base: 0,
    }
    .lookup(
      &StackMemory {
        address: base,
        bytes: &bytes
      },
      0x110
    ),
    Ok(Some(0x0400_0000))
  );
}

#[test]
fn first_level_search_selects_the_page_and_uses_its_sentinel() {
  let mut bytes = [0; 192];
  for (i, value) in [1, 28, 0, 28, 0, 32, 3].into_iter().enumerate() {
    word(&mut bytes, i * 4, value);
  }
  for (offset, value) in [
    (32, 0x100),
    (36, 80),
    (44, 0x200),
    (48, 128),
    (56, 0x300),
    (80, 2),
    (84, (1 << 16) | 0x08),
    (88, 0x100),
    (92, 0x0400_0000),
    (128, 2),
    (132, (1 << 16) | 0x08),
    (136, 0x200),
    (140, 0x0200_1000),
  ] {
    word(&mut bytes, offset, value);
  }
  assert_eq!(lookup(&bytes, 0x1ff), Ok(Some(0x0400_0000)));
  assert_eq!(lookup(&bytes, 0x200), Ok(Some(0x0200_1000)));
  assert_eq!(lookup(&bytes, 0x2ff), Ok(Some(0x0200_1000)));
  assert_eq!(lookup(&bytes, 0x300), Ok(None));
}

// These are complete __unwind_info sections emitted by Apple clang/ld in Xcode 26.5, checked with
// `xcrun dyld_info -unwind`. Regenerate with:
//   xcrun clang -arch arm64 -O2 -dynamiclib fixture.c -fomit-frame-pointer -o leaf.dylib
//   xcrun clang -arch arm64 -O2 -dynamiclib fixture.c -fno-omit-frame-pointer \
//     -mno-omit-leaf-frame-pointer -o frame.dylib
// Extract __unwind_info using its section_64.offset and size (not its vmaddr). fixture.c:
//   __attribute__((noinline)) int leaf(int value) {
//     volatile int values[8];
//     values[0] = value; values[7] = value + 1;
//     return values[0] + values[7];
//   }
//   __attribute__((noinline)) int frame(int value) { return leaf(value) + leaf(value + 2); }
#[test]
fn reads_linker_emitted_arm64_sections() {
  let leaf = [
    1,
    0x1c,
    0,
    0x1c,
    0,
    0x1c,
    2,
    0x338,
    0x40,
    0x40,
    0x388,
    0,
    0x40,
    0,
    0,
    0,
    3,
    0x0002_000c,
    0x0002_0014,
    0,
    0x0100_0024,
    0x0200_1000,
    0x0300_0014,
    0,
  ];
  let framed = [
    1,
    0x1c,
    0,
    0x1c,
    0,
    0x1c,
    2,
    0x2e8,
    0x40,
    0x40,
    0x348,
    0,
    0x40,
    0,
    0,
    0,
    3,
    0x0002_000c,
    0x0002_0014,
    0,
    0x0100_0030,
    0x0400_0000,
    0x0400_0001,
    0,
  ];
  for (words, first, second, end, first_encoding, second_encoding) in [
    (leaf, 0x338, 0x35c, 0x388, 0x0200_1000, 0x0300_0014),
    (framed, 0x2e8, 0x318, 0x348, 0x0400_0000, 0x0400_0001),
  ] {
    let mut bytes = [0; 96];
    for (i, value) in words.into_iter().enumerate() {
      word(&mut bytes, i * 4, value);
    }
    assert_eq!(lookup(&bytes, first), Ok(Some(first_encoding)));
    assert_eq!(lookup(&bytes, second - 1), Ok(Some(first_encoding)));
    assert_eq!(lookup(&bytes, second), Ok(Some(second_encoding)));
    assert_eq!(lookup(&bytes, end), Ok(None));
  }
}
