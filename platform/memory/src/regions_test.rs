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
fn image_rows_point_into_the_table_and_are_sorted() {
  let images = ImageTable::survey();
  let report = walk(Some(&images));
  assert!(!report.by_image.is_empty());
  assert!(report.by_image.iter().all(|row| row.image < images.len()));
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

#[test]
fn shared_pages_are_never_charged() {
  // `SM_SHARED` is 4 in `<mach/vm_region.h>`; 3 is `SM_EMPTY`, a region with no pages.
  assert_eq!(split_dirty(4, 1, 8, 0), (0, 8));
  assert_eq!(split_dirty(3, 1, 8, 0), (8, 0));
  assert_eq!(split_dirty(SM_TRUESHARED, 2, 8, 0), (0, 8));
}

#[test]
fn copy_on_write_pages_only_this_region_references_are_private() {
  assert_eq!(split_dirty(SM_COW, 3, 10, 4), (4, 6));
  assert_eq!(split_dirty(SM_COW, 3, 10, 12), (10, 0));
  assert_eq!(split_dirty(SM_COW, 1, 10, 0), (10, 0));
}

unsafe extern "C" {
  /// From `<mach/mach_vm.h>`.
  fn mach_vm_protect(
    target_task: libc::mach_port_t,
    address: u64,
    size: u64,
    set_maximum: libc::boolean_t,
    new_protection: libc::c_int,
  ) -> libc::kern_return_t;
}

#[test]
fn dirty_pages_count_after_the_maximum_protection_drops_writing() {
  let _lock = memory_lock();
  let region = TaggedRegion::dirty();
  let address = region.0 as usize;
  // SAFETY: lowers the protection of the mapping `region` owns.
  let status = unsafe {
    mach_vm_protect(
      crate::sys::mach::task_self(),
      address as u64,
      REGION_SIZE as u64,
      1,
      PROT_READ,
    )
  };
  assert_eq!(status, 0);

  let info = region_short_info(address, 0).unwrap();
  assert_eq!(info.address, address);
  assert_eq!(
    (info.info.protection | info.info.max_protection) & VM_PROT_WRITE,
    0
  );
  assert!(may_hold_dirty_pages(&info.info, 0));
  let dirtied = region_info(address, 0).unwrap().info.pages_dirtied;
  assert!(u64::from(dirtied) * kernel_page_size() >= REGION_SIZE as u64 / 2);
}

#[test]
fn a_read_only_file_mapping_holds_no_dirty_pages() {
  let path = std::ffi::CString::new(std::env::current_exe().unwrap().to_str().unwrap()).unwrap();
  // SAFETY: a read-only descriptor and mapping, both closed below.
  unsafe {
    let descriptor = libc::open(path.as_ptr(), libc::O_RDONLY);
    assert!(descriptor >= 0);
    let size = crate::sys::mach::page_size();
    let mapping = mmap(
      std::ptr::null_mut(),
      size,
      PROT_READ,
      libc::MAP_SHARED,
      descriptor,
      0,
    );
    assert_ne!(mapping, MAP_FAILED);
    let info = region_short_info(mapping as usize, 0).unwrap();
    assert_eq!(info.address, mapping as usize);
    assert!(!may_hold_dirty_pages(&info.info, 0));
    munmap(mapping, size);
    libc::close(descriptor);
  }
}
