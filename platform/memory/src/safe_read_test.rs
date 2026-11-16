// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;
use libc::{MAP_ANON, MAP_FAILED, MAP_PRIVATE, PROT_READ, PROT_WRITE, mmap, munmap};

#[test]
fn reads_a_live_word() {
  let value = Box::new(0x1234_5678_usize);
  assert_eq!(
    read_word(std::ptr::from_ref(&*value) as usize),
    Some(0x1234_5678)
  );
}

#[test]
fn rejects_a_misaligned_word() {
  let value = Box::new([0usize; 2]);
  assert_eq!(read_word(value.as_ptr() as usize + 1), None);
}

#[test]
fn unmapped_memory_reads_as_none() {
  let size = page_size();
  // SAFETY: an anonymous mapping, unmapped right away.
  let region = unsafe {
    mmap(
      std::ptr::null_mut(),
      size,
      PROT_READ | PROT_WRITE,
      MAP_ANON | MAP_PRIVATE,
      -1,
      0,
    )
  };
  assert_ne!(region, MAP_FAILED);
  // SAFETY: the mapping created above.
  assert_eq!(unsafe { munmap(region, size) }, 0);

  assert_eq!(read_word(region as usize), None);
  assert_eq!(read_string(region as usize, 16), None);
}

#[test]
fn reads_a_string_ending_at_the_end_of_a_mapping() {
  let size = page_size();
  // SAFETY: an anonymous mapping of two pages; the second one is unmapped.
  let region = unsafe {
    mmap(
      std::ptr::null_mut(),
      size * 2,
      PROT_READ | PROT_WRITE,
      MAP_ANON | MAP_PRIVATE,
      -1,
      0,
    )
  };
  assert_ne!(region, MAP_FAILED);
  let region = region as usize;
  // SAFETY: the second page of the mapping above.
  assert_eq!(
    unsafe { munmap((region + size) as *mut libc::c_void, size) },
    0
  );

  let text = b"_ZTS3Foo\0";
  let start = region + size - text.len();
  // SAFETY: the bytes lie within the first, still mapped page.
  unsafe { std::ptr::copy_nonoverlapping(text.as_ptr(), start as *mut u8, text.len()) };

  assert_eq!(read_string(start, 256).unwrap(), b"_ZTS3Foo");
  assert_eq!(read_string(start, 4), None);

  // SAFETY: the first page of the mapping above.
  unsafe { munmap(region as *mut libc::c_void, size) };
}
