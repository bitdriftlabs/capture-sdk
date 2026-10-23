// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used, clippy::float_cmp)]

use super::*;
use crate::report::{Dropped, HeapTimings, ImageChargeRow, Ledgers, Owners, TagRow, Unclassified};
use bd_proto::protos::memory::memory_dump::type_row::Kind;
use bd_proto::protos::memory::memory_dump::{self as proto, MemoryDump};
use protobuf::Message;

fn decode(report: &Report, images: &ImageTable) -> MemoryDump {
  MemoryDump::parse_from_bytes(&memory_dump(report, Some(images))).unwrap()
}

fn counters() -> CountersReport {
  CountersReport {
    phys_footprint: 1,
    phys_footprint_peak: Some(2),
    internal: 3,
    compressed: 4,
    external: 5,
    resident_size: 6,
    decompressions: Some(7),
    limit_bytes_remaining: Some(0),
    available_before_kill: None,
    ledgers: Some(Ledgers {
      graphics: 8,
      media: 9,
      network: 10,
      neural: 11,
      purgeable_volatile: 12,
      purgeable_nonvolatile: 13,
    }),
  }
}

fn regions(image: usize) -> RegionsReport {
  RegionsReport {
    regions: 1,
    truncated: true,
    private_dirty: 2,
    shared_dirty: 3,
    compressed: 4,
    charged: 5,
    virtual_size: 6,
    by_tag: vec![TagRow {
      name: "MALLOC_SMALL".to_string(),
      tag: 7,
      regions: 8,
      private_dirty: 9,
      shared_dirty: 10,
      compressed: 11,
      virtual_size: 12,
      charged: 13,
    }],
    by_image: vec![ImageChargeRow { image, charged: 14 }],
  }
}

fn named(kind: TypeKind, name: &str, blocks: u64) -> TypeRow {
  TypeRow {
    identity: Identity::Named {
      kind,
      name: name.to_string(),
    },
    blocks,
    bytes: blocks * 16,
  }
}

fn heap(image: usize, owners: bool) -> HeapReport {
  let count = |blocks| BlockCount {
    blocks,
    bytes: blocks * 16,
  };
  HeapReport {
    truncated: true,
    total: count(1),
    largest_block_bytes: 2,
    named: count(3),
    swift_boxes: count(4),
    images: count(5),
    reporter: count(6),
    unclassified: Unclassified {
      blocks: 7,
      bytes: 8,
      histogram: vec![0, 9, 0, 10],
    },
    owners: owners.then(|| Owners {
      blocks: 11,
      bytes: 12,
      shared_blocks: 13,
      table_overflow: 14,
      by_type: vec![named(TypeKind::Class, "UIView", 15)],
    }),
    dropped: Dropped {
      skipped_blocks: 16,
      candidate_overflow: 17,
      unreadable_candidates: 18,
    },
    timings: HeapTimings {
      lock_held_ms: 1.5,
      lock_wait_ms: 2.5,
      resolve_ms: 3.5,
      owners_ms: 4.5,
    },
    by_type: vec![
      named(TypeKind::Class, "_TtC7Example4Feed", 19),
      named(TypeKind::Cxx, "N5caulk4FileE", 20),
      TypeRow {
        identity: Identity::Address {
          image,
          offset: 0x4010,
        },
        blocks: 21,
        bytes: 22,
      },
    ],
    zones: vec![ZoneRow {
      name: "DefaultMallocZone".to_string(),
      blocks: 23,
      bytes: 24,
      lock_held_ms: 5.5,
      enumerated: true,
      seen: true,
      skipped: false,
      truncated: true,
    }],
  }
}

#[test]
fn every_counter_round_trips() {
  let dump = decode(
    &Report {
      counters: Some(counters()),
      regions: None,
      heap: None,
    },
    &ImageTable::survey(),
  );
  let counters = dump.counters.unwrap();
  assert_eq!(counters.phys_footprint, 1);
  assert_eq!(counters.phys_footprint_peak, Some(2));
  assert_eq!(counters.internal, 3);
  assert_eq!(counters.compressed, 4);
  assert_eq!(counters.external, 5);
  assert_eq!(counters.resident_size, 6);
  assert_eq!(counters.decompressions, Some(7));
  assert_eq!(counters.limit_bytes_remaining, Some(0));
  assert_eq!(counters.available_before_kill, None);
  let ledgers = counters.ledgers.unwrap();
  assert_eq!(
    [
      ledgers.graphics,
      ledgers.media,
      ledgers.network,
      ledgers.neural,
      ledgers.purgeable_volatile,
      ledgers.purgeable_nonvolatile,
    ],
    [8, 9, 10, 11, 12, 13]
  );
  assert!(dump.regions.is_none());
  assert!(dump.heap.is_none());
  assert!(dump.images.is_empty());
}

#[test]
fn every_region_field_round_trips() {
  let images = ImageTable::survey();
  let dump = decode(
    &Report {
      counters: None,
      regions: Some(regions(3)),
      heap: None,
    },
    &images,
  );
  let regions = dump.regions.unwrap();
  assert_eq!(regions.regions, 1);
  assert!(regions.truncated);
  assert_eq!(
    [
      regions.private_dirty,
      regions.shared_dirty,
      regions.compressed,
      regions.charged,
      regions.virtual_size,
    ],
    [2, 3, 4, 5, 6]
  );
  let tag = &regions.by_tag[0];
  assert_eq!(tag.name, "MALLOC_SMALL");
  assert_eq!([tag.tag, tag.regions], [7, 8]);
  assert_eq!(
    [
      tag.private_dirty,
      tag.shared_dirty,
      tag.compressed,
      tag.virtual_size,
      tag.charged,
    ],
    [9, 10, 11, 12, 13]
  );
  assert_eq!(regions.by_image[0].image, 0);
  assert_eq!(regions.by_image[0].charged, 14);
  assert_eq!(dump.images.len(), 1);
  assert_eq!(dump.images[0].name, images.name(3).unwrap());
  assert_eq!(dump.images[0].uuid, images.uuid(3).unwrap());
}

#[test]
fn every_heap_field_round_trips() {
  let dump = decode(
    &Report {
      counters: None,
      regions: None,
      heap: Some(heap(2, true)),
    },
    &ImageTable::survey(),
  );
  let heap = dump.heap.unwrap();
  assert!(heap.truncated);
  let count = |count: &proto::BlockCount| (count.blocks, count.bytes);
  assert_eq!(count(&heap.total), (1, 16));
  assert_eq!(heap.largest_block_bytes, 2);
  assert_eq!(count(&heap.named), (3, 48));
  assert_eq!(count(&heap.swift_boxes), (4, 64));
  assert_eq!(count(&heap.images), (5, 80));
  assert_eq!(count(&heap.reporter), (6, 96));
  assert_eq!((heap.unclassified.blocks, heap.unclassified.bytes), (7, 8));
  assert_eq!(heap.unclassified.histogram, vec![0, 9, 0, 10]);

  let owners = heap.owners.as_ref().unwrap();
  assert_eq!(
    [
      owners.blocks,
      owners.bytes,
      owners.shared_blocks,
      owners.table_overflow,
    ],
    [11, 12, 13, 14]
  );
  assert_eq!(owners.by_type[0].name(), "UIView");

  assert_eq!(
    [
      heap.dropped.skipped_blocks,
      heap.dropped.candidate_overflow,
      heap.dropped.unreadable_candidates,
    ],
    [16, 17, 18]
  );
  assert_eq!(
    [
      heap.timings.lock_held_ms,
      heap.timings.lock_wait_ms,
      heap.timings.resolve_ms,
      heap.timings.owners_ms,
    ],
    [1.5, 2.5, 3.5, 4.5]
  );

  let zone = &heap.zones[0];
  assert_eq!(zone.name, "DefaultMallocZone");
  assert_eq!([zone.blocks, zone.bytes], [23, 24]);
  assert_eq!(zone.lock_held_ms, 5.5);
  assert_eq!(
    [zone.enumerated, zone.seen, zone.skipped, zone.truncated],
    [true, true, false, true]
  );
}

#[test]
fn type_rows_carry_a_name_and_kind_or_an_address() {
  let dump = decode(
    &Report {
      counters: None,
      regions: None,
      heap: Some(heap(2, false)),
    },
    &ImageTable::survey(),
  );
  let rows = &dump.heap.by_type;
  assert_eq!(rows[0].name(), "_TtC7Example4Feed");
  assert_eq!(rows[0].kind.enum_value(), Ok(Kind::CLASS));
  assert_eq!((rows[0].blocks, rows[0].bytes), (19, 304));
  assert_eq!(rows[1].name(), "N5caulk4FileE");
  assert_eq!(rows[1].kind.enum_value(), Ok(Kind::CXX));

  assert!(!rows[2].has_name());
  assert_eq!(rows[2].kind.enum_value(), Ok(Kind::UNSPECIFIED));
  assert_eq!(rows[2].address().image, 0);
  assert_eq!(rows[2].address().offset, 0x4010);
  assert_eq!((rows[2].blocks, rows[2].bytes), (21, 22));
  assert!(dump.heap.owners.is_none());
}

#[test]
fn images_are_listed_once_in_order_of_first_reference() {
  let images = ImageTable::survey();
  let dump = decode(
    &Report {
      counters: None,
      regions: Some(regions(5)),
      heap: Some(heap(2, false)),
    },
    &images,
  );
  assert_eq!(dump.regions.by_image[0].image, 0);
  assert_eq!(dump.heap.by_type[2].address().image, 1);
  let names: Vec<&str> = dump
    .images
    .iter()
    .map(|image| image.name.as_str())
    .collect();
  assert_eq!(names, [images.name(5).unwrap(), images.name(2).unwrap()]);
}
