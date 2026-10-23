// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;
use crate::test_support::memory_lock;
use bd_proto::protos::memory::memory_dump::MemoryDump;
use protobuf::Message;

fn capture_dump(stages: Stages) -> (MemoryDump, Vec<(String, String)>) {
  let _lock = memory_lock();
  let dump = capture(&Request {
    stages,
    heap: HeapOptions {
      budget_ms: 1_000,
      owners: true,
    },
  })
  .unwrap();
  (
    MemoryDump::parse_from_bytes(&dump.attachment).unwrap(),
    dump.context,
  )
}

fn context_value<'a>(context: &'a [(String, String)], key: &str) -> Option<&'a str> {
  context
    .iter()
    .find(|(name, _)| name == key)
    .map(|(_, value)| value.as_str())
}

#[test]
fn only_requested_stages_are_reported() {
  let (dump, context) = capture_dump(Stages {
    regions: true,
    ..Stages::default()
  });
  assert!(dump.counters.is_none());
  assert!(dump.heap.is_none());
  assert!(dump.regions.charged > 0);
  assert_eq!(context_value(&context, "stages"), Some("regions"));
  assert!(context_value(&context, "regions_ms").is_some());
}

#[test]
fn every_stage_runs_in_order() {
  let (dump, context) = capture_dump(Stages {
    counters: true,
    regions: true,
    heap: true,
  });
  assert!(dump.counters.phys_footprint > 0);
  assert!(!dump.heap.by_type.is_empty());
  assert!(dump.heap.owners.is_some());
  assert_eq!(
    context_value(&context, "stages"),
    Some("counters,regions,heap")
  );
  assert_eq!(context_value(&context, "heap_truncated"), Some("false"));
  assert!(context_value(&context, "phys_footprint_before").is_some());
  assert!(context_value(&context, "phys_footprint_after").is_some());
}

#[test]
fn image_references_resolve_to_named_images() {
  let (dump, _) = capture_dump(Stages {
    regions: true,
    heap: true,
    ..Stages::default()
  });
  assert!(!dump.images.is_empty());
  for image in &dump.images {
    assert!(!image.name.is_empty());
    assert!(!image.name.contains('/'));
    assert_eq!(image.uuid.len(), 16);
  }
  let addresses = dump
    .heap
    .by_type
    .iter()
    .filter(|row| row.has_address())
    .map(|row| row.address().image);
  let charged = dump.regions.by_image.iter().map(|row| row.image);
  for image in addresses.chain(charged) {
    assert!((image as usize) < dump.images.len());
  }
}

#[test]
fn payload_carries_no_absolute_addresses() {
  let (dump, _) = capture_dump(Stages {
    heap: true,
    ..Stages::default()
  });
  assert!(
    dump
      .heap
      .by_type
      .iter()
      .filter(|row| row.has_address())
      .all(|row| row.address().offset < 1 << 32)
  );
}
