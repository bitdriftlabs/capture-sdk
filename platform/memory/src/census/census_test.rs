// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;
use crate::test_support::memory_lock;
use libc::{
  MAP_ANON,
  MAP_FAILED,
  MAP_PRIVATE,
  PROT_READ,
  PROT_WRITE,
  c_char,
  c_void,
  calloc,
  free,
  malloc,
  mmap,
  munmap,
};
use objc::declare::ClassDecl;
use objc::runtime::{Class, Object};
use objc::{class, msg_send, sel, sel_impl};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, OnceLock};
use std::thread;

unsafe extern "C" {
  fn malloc_create_zone(start_size: usize, flags: u32) -> *mut c_void;
  fn malloc_set_zone_name(zone: *mut c_void, name: *const c_char);
  fn malloc_zone_malloc(zone: *mut c_void, size: usize) -> *mut c_void;
  fn malloc_destroy_zone(zone: *mut c_void);
}

const BUFFER_IVAR: &str = "buffer";

/// A class whose instances hold one word, declared once per process.
fn holder_class(name: &'static str, cell: &'static OnceLock<&'static Class>) -> &'static Class {
  cell.get_or_init(|| {
    let mut declaration = ClassDecl::new(name, class!(NSObject)).unwrap();
    declaration.add_ivar::<usize>(BUFFER_IVAR);
    declaration.register()
  })
}

fn start_holder() -> &'static Class {
  static CLASS: OnceLock<&'static Class> = OnceLock::new();
  holder_class("BdMemoryDumpStartHolder", &CLASS)
}

fn interior_holder() -> &'static Class {
  static CLASS: OnceLock<&'static Class> = OnceLock::new();
  holder_class("BdMemoryDumpInteriorHolder", &CLASS)
}

//
// Fixture
//

/// Objects and blocks kept alive while a census runs, released on drop.
#[derive(Default)]
struct Fixture {
  objects: Vec<usize>,
  blocks: Vec<usize>,
}

impl Fixture {
  fn block(&mut self, size: usize) -> usize {
    // SAFETY: freed on drop.
    let block = unsafe { calloc(1, size) } as usize;
    assert_ne!(block, 0);
    self.blocks.push(block);
    block
  }

  fn holder(&mut self, class: &Class, word: usize) {
    // SAFETY: `class` declares the ivar; the object is released on drop.
    unsafe {
      let object: *mut Object = msg_send![class, new];
      (*object).set_ivar::<usize>(BUFFER_IVAR, word);
      self.objects.push(object as usize);
    }
  }
}

impl Drop for Fixture {
  fn drop(&mut self) {
    for object in &self.objects {
      // SAFETY: created with +new in `holder`.
      let (): () = unsafe { msg_send![*object as *mut Object, release] };
    }
    for block in &self.blocks {
      // SAFETY: allocated in `block`.
      unsafe { free(*block as *mut c_void) };
    }
  }
}

fn census(owners: bool) -> HeapReport {
  census_with_budget(1_000, owners)
}

fn census_with_budget(budget_ms: u64, owners: bool) -> HeapReport {
  capture(
    HeapOptions { budget_ms, owners },
    Some(&ImageTable::survey()),
  )
}

fn owned_by<'a>(report: &'a HeapReport, class: &str) -> Option<&'a crate::report::TypeRow> {
  report
    .owners
    .as_ref()?
    .by_type
    .iter()
    .find(|row| row.name == class)
}

#[test]
fn large_block_is_enumerated() {
  let _lock = memory_lock();
  let mut fixture = Fixture::default();
  fixture.block(8 << 20);
  let report = census(false);
  assert!(!report.truncated);
  assert!(report.largest_block_bytes >= 8 << 20);
}

#[test]
fn instances_of_a_known_class_are_counted_exactly() {
  let _lock = memory_lock();
  let blocks_of_holder = |report: &HeapReport| {
    report
      .by_type
      .iter()
      .find(|row| row.name == "BdMemoryDumpStartHolder")
      .map_or(0, |row| row.blocks)
  };
  // The runtime keeps a few blocks of its own that start with the class pointer, so the instances
  // are measured as a difference.
  start_holder();
  let before = blocks_of_holder(&census(false));
  let mut fixture = Fixture::default();
  for _ in 0 .. 4_000 {
    fixture.holder(start_holder(), 0);
  }
  let report = census(false);
  assert_eq!(blocks_of_holder(&report) - before, 4_000);
}

#[test]
fn zero_budget_is_reported_as_truncated() {
  let _lock = memory_lock();
  let report = census_with_budget(0, false);
  assert!(report.truncated);
  assert!(!report.zones.is_empty());
  assert!(report.zones.iter().all(|zone| !zone.enumerated));
  assert_eq!(report.total.blocks, 0);
}

#[test]
fn buffer_is_attributed_to_the_object_holding_it() {
  let _lock = memory_lock();
  let mut fixture = Fixture::default();
  for _ in 0 .. 200 {
    let buffer = fixture.block(4_096);
    fixture.holder(start_holder(), buffer);
  }
  let report = census(true);
  let row = owned_by(&report, "BdMemoryDumpStartHolder").unwrap();
  assert!(row.blocks >= 200);
  assert!(row.bytes >= 200 * 4_096);
  let owners = report.owners.as_ref().unwrap();
  assert!(owners.bytes <= report.unclassified.bytes);
  assert!(owners.blocks <= report.unclassified.blocks);
  assert_eq!(owners.table_overflow, 0);
}

#[test]
fn large_buffer_is_attributed_to_the_object_holding_its_start() {
  let _lock = memory_lock();
  let mut fixture = Fixture::default();
  let buffer = fixture.block(8 << 20);
  fixture.holder(start_holder(), buffer);
  let report = census(true);
  assert!(owned_by(&report, "BdMemoryDumpStartHolder").unwrap().bytes >= 8 << 20);
}

#[test]
fn pointer_inside_a_large_block_does_not_own_it() {
  let _lock = memory_lock();
  let mut fixture = Fixture::default();
  let buffer = fixture.block(4 << 20);
  fixture.holder(start_holder(), buffer + 4_096);
  assert!(owned_by(&census(true), "BdMemoryDumpStartHolder").is_none());
}

#[test]
fn word_with_bits_above_the_address_does_not_own_a_block() {
  let _lock = memory_lock();
  let mut fixture = Fixture::default();
  let buffer = fixture.block(256);
  fixture.holder(start_holder(), buffer | (0x5a << 48));
  assert!(owned_by(&census(true), "BdMemoryDumpStartHolder").is_none());
}

#[test]
fn pointer_to_the_start_beats_pointer_inside() {
  let _lock = memory_lock();
  let mut fixture = Fixture::default();
  let buffer = fixture.block(1_024);
  fixture.holder(interior_holder(), buffer + 64);
  fixture.holder(start_holder(), buffer);
  let report = census(true);
  assert!(owned_by(&report, "BdMemoryDumpStartHolder").unwrap().bytes >= 1_024);
  assert!(owned_by(&report, "BdMemoryDumpInteriorHolder").is_none());
}

#[test]
fn owners_are_not_attributed_when_disabled() {
  let _lock = memory_lock();
  let mut fixture = Fixture::default();
  let buffer = fixture.block(4_096);
  fixture.holder(start_holder(), buffer);
  let report = census(false);
  assert!(report.owners.is_none());
  let json = serde_json::to_value(&report).unwrap();
  assert!(json.get("owners").is_none());
}

#[test]
fn the_dump_tables_are_reported_apart() {
  let _lock = memory_lock();
  let report = census(true);
  assert!(report.reporter.blocks >= 3);
  assert!(report.reporter.bytes > 0);
}

fn zone_named(name: &str, blocks: usize) -> *mut c_void {
  let name = std::ffi::CString::new(name).unwrap();
  // SAFETY: a new zone, destroyed by the caller.
  unsafe {
    let zone = malloc_create_zone(0, 0);
    malloc_set_zone_name(zone, name.as_ptr());
    for _ in 0 .. blocks {
      malloc_zone_malloc(zone, 128);
    }
    zone
  }
}

#[test]
fn webkit_zone_is_never_locked() {
  let _lock = memory_lock();
  let zone = zone_named("WebKit Malloc", 100);
  let report = census(false);
  // SAFETY: created above.
  unsafe { malloc_destroy_zone(zone) };
  let row = report
    .zones
    .iter()
    .find(|zone| zone.name == "WebKit Malloc")
    .unwrap();
  assert!(row.skipped);
  assert!(!row.enumerated);
  assert_eq!(row.blocks, 0);
}

#[test]
fn other_zones_are_enumerated() {
  let _lock = memory_lock();
  let zone = zone_named("BdMemoryDumpTests", 100);
  let report = census(false);
  // SAFETY: created above.
  unsafe { malloc_destroy_zone(zone) };
  let row = report
    .zones
    .iter()
    .find(|zone| zone.name == "BdMemoryDumpTests")
    .unwrap();
  assert!(!row.skipped);
  assert!(row.enumerated);
  assert!(row.blocks >= 100);
}

#[test]
fn captures_survive_regions_being_mapped_and_unmapped() {
  let _lock = memory_lock();
  let running = Arc::new(AtomicBool::new(true));
  let mapper = {
    let running = running.clone();
    thread::spawn(move || {
      while running.load(Ordering::Relaxed) {
        let size = 48 << 20;
        // SAFETY: an anonymous mapping, dirtied and unmapped right away.
        unsafe {
          let region = mmap(
            std::ptr::null_mut(),
            size,
            PROT_READ | PROT_WRITE,
            MAP_ANON | MAP_PRIVATE,
            -1,
            0,
          );
          if region != MAP_FAILED {
            std::ptr::write_bytes(region.cast::<u8>(), 0x5a, size);
            munmap(region, size);
          }
        }
      }
    })
  };
  for _ in 0 .. 5 {
    assert!(census(true).total.blocks > 0);
  }
  running.store(false, Ordering::Relaxed);
  mapper.join().unwrap();
}

#[test]
fn candidate_unmapped_before_resolution_stays_unresolved() {
  let _lock = memory_lock();
  let size = crate::sys::mach::page_size();
  // SAFETY: an anonymous mapping, unmapped by the seam below.
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
  let region = region as usize;

  let mut blocks = Vec::new();
  for _ in 0 .. 200 {
    // SAFETY: freed below; the first word points at the region.
    unsafe {
      let block = malloc(64).cast::<usize>();
      block.write(region);
      blocks.push(block);
    }
  }

  let mut census = Census::new(
    HeapOptions {
      budget_ms: 1_000,
      owners: false,
    },
    None,
  );
  census.before_resolve = Some(Box::new(move || {
    // SAFETY: the mapping created above.
    assert_eq!(unsafe { munmap(region as *mut c_void, size) }, 0);
  }));
  let report = census.run();

  for block in blocks {
    // SAFETY: allocated above.
    unsafe { free(block.cast()) };
  }
  assert!(report.dropped.unreadable_candidates >= 1);
  assert!(report.unclassified.blocks > 0);
}
