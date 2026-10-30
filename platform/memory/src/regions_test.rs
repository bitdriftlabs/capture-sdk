// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;
use crate::counters;
use crate::test_support::memory_lock;
use libc::{MAP_ANON, MAP_FAILED, MAP_PRIVATE, PROT_READ, PROT_WRITE, c_void, mmap, munmap};

/// `VM_MEMORY_APPLICATION_SPECIFIC_1`, reserved for apps.
const APPLICATION_TAG: u32 = 240;
const REGION_SIZE: usize = 8 << 20;

//
// TaggedRegion
//

struct TaggedRegion(*mut c_void);

impl TaggedRegion {
  fn dirty() -> Self {
    // SAFETY: an anonymous mapping; the tag travels in the file descriptor argument.
    let region = unsafe {
      mmap(
        std::ptr::null_mut(),
        REGION_SIZE,
        PROT_READ | PROT_WRITE,
        MAP_ANON | MAP_PRIVATE,
        (APPLICATION_TAG << 24).cast_signed(),
        0,
      )
    };
    assert_ne!(region, MAP_FAILED);
    // SAFETY: the mapping created above.
    unsafe { std::ptr::write_bytes(region.cast::<u8>(), 0x5a, REGION_SIZE) };
    Self(region)
  }
}

impl Drop for TaggedRegion {
  fn drop(&mut self) {
    // SAFETY: the mapping created in `dirty`.
    unsafe { munmap(self.0, REGION_SIZE) };
  }
}

#[test]
fn charged_bytes_never_exceed_the_footprint() {
  let _lock = memory_lock();
  let report = walk(Some(&ImageTable::survey()));
  let footprint = counters::phys_footprint().unwrap();
  assert!(report.charged > 0);
  assert!(
    report.charged <= footprint,
    "{} > {footprint}",
    report.charged
  );
}

#[test]
fn tagged_region_shows_under_its_tag() {
  let _lock = memory_lock();
  let _region = TaggedRegion::dirty();
  let report = walk(None);
  let row = report
    .by_tag
    .iter()
    .find(|row| row.tag == APPLICATION_TAG)
    .unwrap();
  assert!(row.virtual_size >= REGION_SIZE as u64);
  assert!(row.charged > REGION_SIZE as u64 * 9 / 10);
}

#[test]
fn submaps_do_not_loop() {
  let report = walk(None);
  assert!(!report.truncated);
  assert!(report.regions > 0);
  assert_eq!(
    report.by_tag.iter().map(|row| row.regions).sum::<u32>(),
    report.regions
  );
}

#[test]
fn images_are_named_without_paths_and_sorted() {
  let report = walk(Some(&ImageTable::survey()));
  assert!(!report.by_image.is_empty());
  assert!(report.by_image.iter().all(|row| !row.name.contains('/')));
  assert!(
    report
      .by_image
      .windows(2)
      .all(|pair| pair[0].charged >= pair[1].charged)
  );
}

#[test]
fn walk_without_images_attributes_nothing_to_images() {
  let report = walk(None);
  assert!(report.charged > 0);
  assert!(report.by_image.is_empty());
}
