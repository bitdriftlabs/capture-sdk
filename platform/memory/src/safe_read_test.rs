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
fn reads_live_memory() {
  let value = Box::new(0x1234_5678_usize);
  let mut bytes = [0u8; 8];
  assert!(read(std::ptr::from_ref(&*value) as usize, &mut bytes));
  assert_eq!(usize::from_ne_bytes(bytes), 0x1234_5678);
}

#[test]
fn unmapped_memory_fails_to_read() {
  let size = usize::try_from(crate::sys::mach::kernel_page_size()).unwrap();
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

  assert!(!read(region as usize, &mut [0u8; 8]));
}
